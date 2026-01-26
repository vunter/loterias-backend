package br.com.loterias.service;

import br.com.loterias.domain.dto.AnaliseNumeroResponse;
import br.com.loterias.domain.dto.AnaliseNumeroResponse.*;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AnaliseNumeroService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseNumeroService.class);

    private final ConcursoRepository concursoRepository;

    public AnaliseNumeroService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public AnaliseNumeroResponse analisarNumero(TipoLoteria tipo, int numero) {
        log.info("Analisando número {} para {}", numero, tipo.getNome());

        if (numero < tipo.getNumeroInicial() || numero > tipo.getNumeroFinal()) {
            log.error("Número inválido para análise: numero={}, tipo={}, range=[{}, {}]", numero, tipo.getNome(), tipo.getNumeroInicial(), tipo.getNumeroFinal());
            throw new IllegalArgumentException("Número " + numero + " inválido para " + tipo.getNome());
        }

        Map<Integer, Long> frequenciaMap = new HashMap<>();
        List<Object[]> freqData = concursoRepository.findFrequenciaDezenas(tipo.name());
        for (Object[] row : freqData) {
            frequenciaMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        long frequencia = frequenciaMap.getOrDefault(numero, 0L);

        Optional<Integer> maxNumeroOpt = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        int ultimoConcurso = maxNumeroOpt.orElse(0);

        Map<Integer, Integer> ultimaAparicaoMap = new HashMap<>();
        List<Object[]> ultAparicaoData = concursoRepository.findUltimaAparicaoDezenas(tipo.name());
        for (Object[] row : ultAparicaoData) {
            ultimaAparicaoMap.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }

        int ultimaAparicao = ultimaAparicaoMap.getOrDefault(numero, 0);
        int atrasoAtual = ultimoConcurso - ultimaAparicao;

        long totalConcursos = concursoRepository.countByTipoLoteria(tipo);
        double percentual = totalConcursos > 0 ? (frequencia * 100.0 / totalConcursos) : 0;
        double mediaAtraso = frequencia > 0 ? (double) totalConcursos / frequencia : 0;

        EstatisticasNumero stats = new EstatisticasNumero(
                frequencia, Math.round(percentual * 100.0) / 100.0,
                atrasoAtual, atrasoAtual, Math.round(mediaAtraso * 100.0) / 100.0,
                null, null
        );

        List<Integer> topCompanheiros = calcularCompanheiros(tipo, numero, frequenciaMap);

        TendenciaNumero tendencia = calcularTendencia(atrasoAtual, mediaAtraso, frequencia, totalConcursos);
        log.debug("Análise número concluída: numero={}, frequencia={}, atraso={}, tendencia={}", numero, frequencia, atrasoAtual, tendencia.status());

        return new AnaliseNumeroResponse(numero, tipo.getNome(), stats, List.of(), topCompanheiros, tendencia);
    }

    private List<Integer> calcularCompanheiros(TipoLoteria tipo, int numero, Map<Integer, Long> frequenciaMap) {
        return frequenciaMap.entrySet().stream()
                .filter(e -> e.getKey() != numero)
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
    }

    private TendenciaNumero calcularTendencia(int atrasoAtual, double mediaAtraso, long frequencia, long totalConcursos) {
        String status;
        String recomendacao;
        int score;

        double razaoAtraso = mediaAtraso > 0 ? atrasoAtual / mediaAtraso : 1;

        if (razaoAtraso > 1.5) {
            status = "ATRASADO";
            recomendacao = "Número está muito acima da média de atraso - pode estar 'devido'";
            score = 80 + (int) Math.min(razaoAtraso * 5, 20);
        } else if (razaoAtraso < 0.5) {
            status = "RECENTE";
            recomendacao = "Número saiu recentemente - estatisticamente pode demorar a sair novamente";
            score = 30;
        } else if (frequencia > totalConcursos / 10) {
            status = "QUENTE";
            recomendacao = "Número sai com frequência acima da média";
            score = 70;
        } else if (frequencia < totalConcursos / 20) {
            status = "FRIO";
            recomendacao = "Número sai com frequência abaixo da média";
            score = 40;
        } else {
            status = "NEUTRO";
            recomendacao = "Número está dentro dos padrões estatísticos esperados";
            score = 50;
        }

        return new TendenciaNumero(status, recomendacao, Math.min(score, 100));
    }

    public List<AnaliseNumeroResponse> analisarTodosNumeros(TipoLoteria tipo) {
        log.info("Analisando todos os números de {}", tipo.getNome());

        Map<Integer, Long> frequenciaMap = new HashMap<>();
        List<Object[]> freqData = concursoRepository.findFrequenciaDezenas(tipo.name());
        for (Object[] row : freqData) {
            frequenciaMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        Optional<Integer> maxNumeroOpt = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        int ultimoConcurso = maxNumeroOpt.orElse(0);

        Map<Integer, Integer> ultimaAparicaoMap = new HashMap<>();
        List<Object[]> ultAparicaoData = concursoRepository.findUltimaAparicaoDezenas(tipo.name());
        for (Object[] row : ultAparicaoData) {
            ultimaAparicaoMap.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }

        long totalConcursos = concursoRepository.countByTipoLoteria(tipo);

        int limiteDezenas = 100 * tipo.getMinimoNumeros();
        List<Object[]> dadosRecentes = concursoRepository.findNumerosEDezenasLimitado(tipo.name(), limiteDezenas);
        
        Map<Integer, List<Integer>> aparicoesPorNumero = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> companheirosCount = new HashMap<>();
        
        Map<Integer, List<Integer>> concursosMap = new LinkedHashMap<>();
        for (Object[] row : dadosRecentes) {
            int numeroConcurso = ((Number) row[0]).intValue();
            int dezena = ((Number) row[1]).intValue();
            concursosMap.computeIfAbsent(numeroConcurso, k -> new ArrayList<>()).add(dezena);
        }
        
        for (Map.Entry<Integer, List<Integer>> entry : concursosMap.entrySet()) {
            int numeroConcurso = entry.getKey();
            List<Integer> dezenas = entry.getValue();
            
            for (Integer dezena : dezenas) {
                aparicoesPorNumero.computeIfAbsent(dezena, k -> new ArrayList<>()).add(numeroConcurso);
                
                for (Integer outra : dezenas) {
                    if (!outra.equals(dezena)) {
                        companheirosCount.computeIfAbsent(dezena, k -> new HashMap<>())
                                .merge(outra, 1, Integer::sum);
                    }
                }
            }
        }

        List<AnaliseNumeroResponse> analises = new ArrayList<>();
        log.debug("Dados carregados para análise geral: totalConcursos={}, numerosRange=[{}, {}]", totalConcursos, tipo.getNumeroInicial(), tipo.getNumeroFinal());
        for (int numero = tipo.getNumeroInicial(); numero <= tipo.getNumeroFinal(); numero++) {
            long frequencia = frequenciaMap.getOrDefault(numero, 0L);
            int ultimaAparicao = ultimaAparicaoMap.getOrDefault(numero, 0);
            int atrasoAtual = ultimoConcurso - ultimaAparicao;
            double percentual = totalConcursos > 0 ? (frequencia * 100.0 / totalConcursos) : 0;
            double mediaAtraso = frequencia > 0 ? (double) totalConcursos / frequencia : 0;

            EstatisticasNumero stats = new EstatisticasNumero(
                    frequencia, Math.round(percentual * 100.0) / 100.0,
                    atrasoAtual, atrasoAtual, Math.round(mediaAtraso * 100.0) / 100.0,
                    null, null
            );

            List<Integer> ultimasCinco = aparicoesPorNumero.getOrDefault(numero, List.of())
                    .stream().limit(5).toList();

            List<Integer> companheiros = companheirosCount.getOrDefault(numero, Map.of())
                    .entrySet().stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();

            TendenciaNumero tendencia = calcularTendencia(atrasoAtual, mediaAtraso, frequencia, totalConcursos);

            analises.add(new AnaliseNumeroResponse(numero, tipo.getNome(), stats, ultimasCinco, companheiros, tendencia));
        }

        log.info("Análise de todos os números concluída: tipo={}, numerosAnalisados={}", tipo.getNome(), analises.size());
        return analises;
    }
}
