package br.com.loterias.service;

import br.com.loterias.domain.dto.ConferirApostaResponse;
import br.com.loterias.domain.dto.ConferirApostaResponse.*;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ConferirApostaService {

    private static final Logger log = LoggerFactory.getLogger(ConferirApostaService.class);

    private final ConcursoRepository concursoRepository;

    public ConferirApostaService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    private static final int LIMITE_CONCURSOS = 500;

    public ConferirApostaResponse conferirNoHistorico(TipoLoteria tipo, List<Integer> numeros) {
        log.info("Conferindo aposta {} nos últimos {} concursos de {}", numeros, LIMITE_CONCURSOS, tipo.getNome());

        int limiteDezenas = LIMITE_CONCURSOS * tipo.getMinimoNumeros();
        List<Object[]> dados = concursoRepository.findNumerosEDezenasLimitado(tipo.name(), limiteDezenas);

        Map<Integer, List<Integer>> concursosMap = new LinkedHashMap<>();
        for (Object[] row : dados) {
            int numeroConcurso = ((Number) row[0]).intValue();
            int dezena = ((Number) row[1]).intValue();
            concursosMap.computeIfAbsent(numeroConcurso, k -> new ArrayList<>()).add(dezena);
        }

        Set<Integer> apostados = new HashSet<>(numeros);
        List<ResultadoConferencia> premiados = new ArrayList<>();
        int maiorAcertos = 0;
        int concursoMaiorAcertos = 0;
        int minAcertos = getMinimosAcertosParaPremio(tipo);
        log.debug("Dados carregados: concursos={}, minAcertosParaPremio={}", concursosMap.size(), minAcertos);

        for (Map.Entry<Integer, List<Integer>> entry : concursosMap.entrySet()) {
            int numeroConcurso = entry.getKey();
            List<Integer> dezenas = entry.getValue();

            List<Integer> acertos = dezenas.stream()
                    .filter(apostados::contains)
                    .sorted()
                    .collect(Collectors.toList());

            int qtdAcertos = acertos.size();
            if (qtdAcertos > maiorAcertos) {
                maiorAcertos = qtdAcertos;
                concursoMaiorAcertos = numeroConcurso;
            }

            if (qtdAcertos >= minAcertos) {
                String faixaDesc = getFaixaDescricao(tipo, qtdAcertos);
                premiados.add(new ResultadoConferencia(
                        numeroConcurso, null, dezenas, acertos,
                        qtdAcertos, faixaDesc, BigDecimal.ZERO
                ));
            }
        }

        premiados.sort((a, b) -> Integer.compare(b.numeroConcurso(), a.numeroConcurso()));

        int totalConcursos = concursosMap.size();
        double percentual = totalConcursos > 0 ? (premiados.size() * 100.0 / totalConcursos) : 0;

        ResumoConferencia resumo = new ResumoConferencia(
                totalConcursos, premiados.size(), Math.round(percentual * 100.0) / 100.0,
                BigDecimal.ZERO, maiorAcertos, concursoMaiorAcertos
        );

        log.info("Conferência concluída: tipo={}, concursos={}, premiados={}, maiorAcertos={}", tipo.getNome(), totalConcursos, premiados.size(), maiorAcertos);

        return new ConferirApostaResponse(tipo, numeros, resumo, premiados.stream().limit(50).toList());
    }

    private int getMinimosAcertosParaPremio(TipoLoteria tipo) {
        return switch (tipo) {
            case MEGA_SENA -> 4;
            case LOTOFACIL -> 11;
            case QUINA -> 2;
            case LOTOMANIA -> 15;
            case TIMEMANIA -> 3;
            case DUPLA_SENA -> 3;
            case DIA_DE_SORTE -> 4;
            case SUPER_SETE -> 3;
            case MAIS_MILIONARIA -> 2;
        };
    }

    private String getFaixaDescricao(TipoLoteria tipo, int acertos) {
        if (tipo == TipoLoteria.MAIS_MILIONARIA) {
            return acertos + " acertos (sem trevos)";
        }
        return acertos + " acertos";
    }
}
