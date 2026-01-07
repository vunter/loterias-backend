package br.com.loterias.service;

import br.com.loterias.domain.dto.PremiacaoSimulada;
import br.com.loterias.domain.dto.SimularApostasRequest;
import br.com.loterias.domain.dto.SimularApostasResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.service.util.AcertosPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SimuladorApostasService {

    private static final Logger log = LoggerFactory.getLogger(SimuladorApostasService.class);

    private final ConcursoRepository concursoRepository;

    public SimuladorApostasService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public SimularApostasResponse simularApostas(TipoLoteria tipo, SimularApostasRequest request) {
        log.info("Simulando apostas: tipo={}, jogos={}, concursoInicio={}, concursoFim={}", tipo.getNome(), request.jogos().size(), request.concursoInicio(), request.concursoFim());
        // Use range query if both bounds are specified; otherwise fetch all and filter
        List<Concurso> concursosFiltrados;
        if (request.concursoInicio() != null && request.concursoFim() != null) {
            concursosFiltrados = concursoRepository.findByTipoLoteriaAndNumeroRange(
                    tipo, request.concursoInicio(), request.concursoFim());
        } else {
            List<Concurso> todosConcursos = concursoRepository.findByTipoLoteriaWithDezenas(tipo);
            concursosFiltrados = filtrarConcursos(todosConcursos, request);
        }
        
        List<PremiacaoSimulada> premiacoes = new ArrayList<>();
        Map<String, Integer> distribuicaoAcertos = new LinkedHashMap<>();
        BigDecimal totalPremios = BigDecimal.ZERO;
        
        for (Concurso concurso : concursosFiltrados) {
            Set<Integer> dezenasSorteadasSet = new HashSet<>(concurso.getDezenasSorteadas());
            
            for (List<Integer> jogo : request.jogos()) {
                int acertos = contarAcertos(jogo, dezenasSorteadasSet);
                
                String chaveAcertos = acertos + " acertos";
                distribuicaoAcertos.merge(chaveAcertos, 1, Integer::sum);
                
                FaixaPremiacao faixaPremiada = encontrarFaixaPremiada(concurso, acertos);
                if (faixaPremiada != null && faixaPremiada.getValorPremio() != null) {
                    BigDecimal premio = faixaPremiada.getValorPremio();
                    totalPremios = totalPremios.add(premio);
                    
                    premiacoes.add(new PremiacaoSimulada(
                        concurso.getNumero(),
                        concurso.getDataApuracao(),
                        new ArrayList<>(jogo),
                        new ArrayList<>(concurso.getDezenasSorteadas()),
                        acertos,
                        faixaPremiada.getDescricaoFaixa(),
                        premio
                    ));
                }
            }
        }
        
        int totalConcursos = concursosFiltrados.size();
        int totalApostas = totalConcursos * request.jogos().size();
        BigDecimal valorAposta = request.valorAposta() != null ? request.valorAposta() : BigDecimal.ZERO;
        BigDecimal totalInvestido = valorAposta.multiply(BigDecimal.valueOf(totalApostas));
        BigDecimal lucroOuPrejuizo = totalPremios.subtract(totalInvestido);
        
        log.info("Simulação concluída: tipo={}, concursos={}, apostas={}, premiacoes={}, totalPremios={}", tipo.getNome(), totalConcursos, totalApostas, premiacoes.size(), totalPremios);
        
        Double roi = null;
        if (totalInvestido.compareTo(BigDecimal.ZERO) > 0) {
            roi = lucroOuPrejuizo
                .divide(totalInvestido, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        }
        
        Map<String, Integer> distribuicaoOrdenada = distribuicaoAcertos.entrySet().stream()
            .sorted((e1, e2) -> {
                int n1 = Integer.parseInt(e1.getKey().split(" ")[0]);
                int n2 = Integer.parseInt(e2.getKey().split(" ")[0]);
                return Integer.compare(n2, n1);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, _) -> e1,
                LinkedHashMap::new
            ));
        
        return new SimularApostasResponse(
            totalConcursos,
            totalApostas,
            totalInvestido,
            totalPremios,
            lucroOuPrejuizo,
            roi,
            premiacoes,
            distribuicaoOrdenada
        );
    }

    private List<Concurso> filtrarConcursos(List<Concurso> concursos, SimularApostasRequest request) {
        return concursos.stream()
            .filter(c -> request.concursoInicio() == null || c.getNumero() >= request.concursoInicio())
            .filter(c -> request.concursoFim() == null || c.getNumero() <= request.concursoFim())
            .sorted(Comparator.comparing(Concurso::getNumero))
            .collect(Collectors.toList());
    }

    private int contarAcertos(List<Integer> jogo, Set<Integer> dezenasSorteadas) {
        return (int) jogo.stream()
            .filter(dezenasSorteadas::contains)
            .count();
    }

    private FaixaPremiacao encontrarFaixaPremiada(Concurso concurso, int acertos) {
        String acertosStr = String.valueOf(acertos);
        for (FaixaPremiacao faixa : concurso.getFaixasPremiacao()) {
            String descricao = faixa.getDescricaoFaixa();
            if (descricao == null) continue;
            // Use pre-compiled pattern for word-boundary matching
            if (AcertosPatternCache.get(acertos).matcher(descricao).matches()) {
                return faixa;
            }
        }
        return null;
    }
}
