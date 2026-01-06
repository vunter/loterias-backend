package br.com.loterias.service;

import br.com.loterias.domain.dto.ResultadoVerificacao;
import br.com.loterias.domain.dto.ResumoVerificacao;
import br.com.loterias.domain.dto.VerificarApostaRequest;
import br.com.loterias.domain.dto.VerificarApostaResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.service.util.AcertosPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class VerificadorApostasService {

    private static final Logger log = LoggerFactory.getLogger(VerificadorApostasService.class);

    private final ConcursoRepository concursoRepository;

    public VerificadorApostasService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public VerificarApostaResponse verificarAposta(TipoLoteria tipo, VerificarApostaRequest request) {
        log.info("Verificando aposta: tipo={}, numeros={}, concursoInicio={}, concursoFim={}", tipo.getNome(), request.numeros(), request.concursoInicio(), request.concursoFim());
        Integer concursoInicio = request.concursoInicio() != null ? request.concursoInicio() : 1;
        Integer concursoFim = request.concursoFim();

        if (concursoFim == null) {
            concursoFim = concursoRepository.findMaxNumeroByTipoLoteria(tipo).orElse(concursoInicio);
        }

        Set<Integer> numerosApostados = new HashSet<>(request.numeros());
        List<ResultadoVerificacao> resultados = new ArrayList<>();

        int totalAcertos4mais = 0;
        int totalAcertos5mais = 0;
        int totalPremiacoes = 0;
        BigDecimal valorTotalPremios = BigDecimal.ZERO;

        // Batch query instead of N+1 individual queries
        List<Concurso> concursos = concursoRepository.findByTipoLoteriaAndNumeroRange(tipo, concursoInicio, concursoFim);
        log.debug("Concursos carregados para verificação: count={}, range=[{}, {}]", concursos.size(), concursoInicio, concursoFim);

        for (Concurso concurso : concursos) {
            List<Integer> acertos = calcularAcertos(numerosApostados, concurso.getDezenasSorteadas());
            int quantidadeAcertos = acertos.size();

            String faixaPremiacao = null;
            BigDecimal valorPremio = null;

            for (FaixaPremiacao faixa : concurso.getFaixasPremiacao()) {
                if (correspondeAcertos(faixa, quantidadeAcertos)) {
                    faixaPremiacao = faixa.getDescricaoFaixa();
                    valorPremio = faixa.getValorPremio();
                    break;
                }
            }

            resultados.add(new ResultadoVerificacao(
                concurso.getNumero(),
                concurso.getDataApuracao(),
                concurso.getDezenasSorteadas(),
                acertos,
                quantidadeAcertos,
                faixaPremiacao,
                valorPremio
            ));

            if (quantidadeAcertos >= 4) {
                totalAcertos4mais++;
            }
            if (quantidadeAcertos >= 5) {
                totalAcertos5mais++;
            }
            if (valorPremio != null) {
                totalPremiacoes++;
                valorTotalPremios = valorTotalPremios.add(valorPremio);
            }
        }

        ResumoVerificacao resumo = new ResumoVerificacao(
            resultados.size(),
            totalAcertos4mais,
            totalAcertos5mais,
            totalPremiacoes,
            valorTotalPremios
        );

        log.info("Verificação concluída: tipo={}, concursos={}, premiacoes={}, valorTotal={}", tipo.getNome(), resultados.size(), totalPremiacoes, valorTotalPremios);

        return new VerificarApostaResponse(
            request.numeros(),
            resultados,
            resumo
        );
    }

    private List<Integer> calcularAcertos(Set<Integer> numerosApostados, List<Integer> dezenasSorteadas) {
        List<Integer> acertos = new ArrayList<>();
        for (Integer dezena : dezenasSorteadas) {
            if (numerosApostados.contains(dezena)) {
                acertos.add(dezena);
            }
        }
        return acertos;
    }

    private boolean correspondeAcertos(FaixaPremiacao faixa, int quantidadeAcertos) {
        String descricao = faixa.getDescricaoFaixa();
        if (descricao == null) {
            return false;
        }
        // Use pre-compiled pattern for word-boundary matching
        return AcertosPatternCache.get(quantidadeAcertos).matcher(descricao).matches();
    }
}
