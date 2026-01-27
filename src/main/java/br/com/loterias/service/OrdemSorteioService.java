package br.com.loterias.service;

import br.com.loterias.domain.dto.OrdemSorteioAnalise;
import br.com.loterias.domain.dto.OrdemSorteioAnalise.*;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrdemSorteioService {

    private static final Logger log = LoggerFactory.getLogger(OrdemSorteioService.class);

    private final ConcursoRepository concursoRepository;

    public OrdemSorteioService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public OrdemSorteioAnalise analisarOrdemSorteio(TipoLoteria tipo) {
        log.info("Analisando ordem de sorteio para {}", tipo.getNome());

        List<Concurso> concursos = concursoRepository.findByTipoLoteriaWithOrdemSorteio(tipo);
        
        List<Concurso> concursosComOrdem = concursos.stream()
                .filter(c -> c.getDezenasSorteadasOrdemSorteio() != null && !c.getDezenasSorteadasOrdemSorteio().isEmpty())
                .toList();
        log.debug("Concursos com ordem de sorteio: {}/{}", concursosComOrdem.size(), concursos.size());

        if (concursosComOrdem.isEmpty()) {
            log.info("Nenhum concurso com ordem de sorteio encontrado: tipo={}", tipo.getNome());
            return new OrdemSorteioAnalise(
                tipo.name(), tipo.getNome(), 0,
                List.of(), List.of(), Map.of(), List.of()
            );
        }

        Map<Integer, Integer> primeiraBola = new HashMap<>();
        Map<Integer, Integer> ultimaBola = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> frequenciaPorPosicao = new HashMap<>();
        Map<Integer, List<Integer>> posicoesNumero = new HashMap<>();

        for (Concurso c : concursosComOrdem) {
            List<Integer> ordem = c.getDezenasSorteadasOrdemSorteio();
            
            int primeiro = ordem.get(0);
            int ultimo = ordem.get(ordem.size() - 1);
            
            primeiraBola.merge(primeiro, 1, Integer::sum);
            ultimaBola.merge(ultimo, 1, Integer::sum);

            for (int i = 0; i < ordem.size(); i++) {
                int numero = ordem.get(i);
                int posicao = i + 1;
                
                frequenciaPorPosicao
                    .computeIfAbsent(numero, k -> new HashMap<>())
                    .merge(posicao, 1, Integer::sum);
                
                posicoesNumero
                    .computeIfAbsent(numero, k -> new ArrayList<>())
                    .add(posicao);
            }
        }

        int total = concursosComOrdem.size();
        log.info("Análise ordem de sorteio: tipo={}, concursosAnalisados={}", tipo.getNome(), total);

        List<NumeroOrdemInfo> topPrimeiraBola = primeiraBola.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> new NumeroOrdemInfo(e.getKey(), e.getValue(), round(e.getValue() * 100.0 / total)))
                .toList();

        List<NumeroOrdemInfo> topUltimaBola = ultimaBola.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> new NumeroOrdemInfo(e.getKey(), e.getValue(), round(e.getValue() * 100.0 / total)))
                .toList();

        List<NumeroOrdemInfo> mediaOrdem = posicoesNumero.entrySet().stream()
                .map(e -> {
                    double media = e.getValue().stream().mapToInt(i -> i).average().orElse(0);
                    return new NumeroOrdemInfo(e.getKey(), e.getValue().size(), round(media));
                })
                .sorted(Comparator.comparingDouble(NumeroOrdemInfo::percentual))
                .limit(20)
                .toList();

        Map<Integer, List<PosicaoFrequencia>> freqMap = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : frequenciaPorPosicao.entrySet()) {
            int numero = entry.getKey();
            int totalNum = entry.getValue().values().stream().mapToInt(i -> i).sum();
            List<PosicaoFrequencia> posFreqs = entry.getValue().entrySet().stream()
                    .map(e -> new PosicaoFrequencia(e.getKey(), e.getValue(), round(e.getValue() * 100.0 / totalNum)))
                    .sorted(Comparator.comparingInt(PosicaoFrequencia::posicao))
                    .toList();
            freqMap.put(numero, posFreqs);
        }

        return new OrdemSorteioAnalise(
            tipo.name(),
            tipo.getNome(),
            total,
            topPrimeiraBola,
            topUltimaBola,
            freqMap,
            mediaOrdem
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
