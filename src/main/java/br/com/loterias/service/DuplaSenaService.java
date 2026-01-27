package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.dto.DuplaSenaAnalise;
import br.com.loterias.domain.dto.DuplaSenaAnalise.*;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DuplaSenaService {

    private static final Logger log = LoggerFactory.getLogger(DuplaSenaService.class);

    private final ConcursoRepository concursoRepository;

    public DuplaSenaService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'analise-dupla-sena'")
    @Transactional(readOnly = true)
    public DuplaSenaAnalise analisarDuplaSena() {
        log.info("Analisando Dupla Sena");

        int totalCompletos = concursoRepository.countDuplaSenaCompletos();
        if (totalCompletos == 0) {
            log.info("Nenhum concurso completo da Dupla Sena encontrado");
            return criarAnaliseVazia();
        }
        log.debug("Concursos completos Dupla Sena: count={}", totalCompletos);

        // All frequency/coincidence computation done in SQL
        Map<Integer, Integer> freqPrimeiro = queryToFreqMap(concursoRepository.findFreqPrimeiroSorteioDupla());
        Map<Integer, Integer> freqSegundo = queryToFreqMap(concursoRepository.findFreqSegundoSorteioDupla());

        Object[] rawStats = concursoRepository.findCoincidenciasStatsDupla();
        // Native query returns Object[] where first element may be the row itself
        Object[] coincStats = (rawStats.length > 0 && rawStats[0] instanceof Object[]) ? (Object[]) rawStats[0] : rawStats;
        double mediaCoinc = coincStats[0] != null ? ((Number) coincStats[0]).doubleValue() : 0;
        int maxCoinc = coincStats[1] != null ? ((Number) coincStats[1]).intValue() : 0;
        int minCoinc = coincStats[2] != null ? ((Number) coincStats[2]).intValue() : 0;

        Map<Integer, Integer> distribuicaoCoinc = new HashMap<>();
        for (Object[] row : concursoRepository.findDistribuicaoCoincidenciasDupla()) {
            distribuicaoCoinc.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }

        List<Integer> quentesPrimeiro = getTopNumeros(freqPrimeiro, 10);
        List<Integer> quentesSegundo = getTopNumeros(freqSegundo, 10);

        Set<Integer> ambos = new HashSet<>(quentesPrimeiro);
        ambos.retainAll(new HashSet<>(quentesSegundo));

        Set<Integer> exclusivosPrimeiro = new HashSet<>(quentesPrimeiro);
        exclusivosPrimeiro.removeAll(new HashSet<>(quentesSegundo));

        Set<Integer> exclusivosSegundo = new HashSet<>(quentesSegundo);
        exclusivosSegundo.removeAll(new HashSet<>(quentesPrimeiro));

        double correlacao = calcularCorrelacao(freqPrimeiro, freqSegundo);
        log.debug("Correlação entre sorteios: {}, mediaCoinc={}", correlacao, mediaCoinc);

        ComparacaoSorteios comparacao = new ComparacaoSorteios(
            freqPrimeiro, freqSegundo, correlacao, (int) Math.round(mediaCoinc)
        );

        EstatisticasCoincidencia estatsCoinc = new EstatisticasCoincidencia(
            maxCoinc, minCoinc, Math.round(mediaCoinc * 100.0) / 100.0, distribuicaoCoinc
        );

        // Only load last 20 concursos — IDs first, then entities (avoids 4-way cartesian product)
        List<Long> top20Ids = concursoRepository.findTop20DuplaSenaIds();
        List<Concurso> top20Entities = new ArrayList<>(concursoRepository.findAllById(top20Ids));
        top20Entities.sort(Comparator.comparing(Concurso::getNumero).reversed());
        List<ConcursoDuplaSena> ultimosConcursos = top20Entities.stream()
                .map(c -> {
                    Set<Integer> p = new HashSet<>(c.getDezenasSorteadas());
                    Set<Integer> s = new HashSet<>(c.getDezenasSegundoSorteio());
                    p.retainAll(s);

                    List<Integer> ordemSorteio = c.getDezenasSorteadasOrdemSorteio();
                    int half = c.getDezenasSorteadas().size();
                    List<Integer> primeiro = (ordemSorteio != null && ordemSorteio.size() > half)
                            ? List.copyOf(ordemSorteio.subList(0, half)) : c.getDezenasSorteadas();
                    List<Integer> segundo = (ordemSorteio != null && ordemSorteio.size() > half)
                            ? List.copyOf(ordemSorteio.subList(half, ordemSorteio.size())) : c.getDezenasSegundoSorteio();

                    return new ConcursoDuplaSena(
                        c.getNumero(), c.getDataApuracao(), primeiro, segundo,
                        p.size(), getPremioFaixaUm(c),
                        getGanhadoresFaixa(c, 1), getGanhadoresFaixa(c, 4)
                    );
                })
                .toList();

        return new DuplaSenaAnalise(
            totalCompletos, comparacao,
            quentesPrimeiro, quentesSegundo,
            new ArrayList<>(ambos), new ArrayList<>(exclusivosPrimeiro), new ArrayList<>(exclusivosSegundo),
            ultimosConcursos, estatsCoinc
        );
    }

    private Map<Integer, Integer> queryToFreqMap(List<Object[]> rows) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return map;
    }

    private List<Integer> getTopNumeros(Map<Integer, Integer> freq, int limit) {
        return freq.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double calcularCorrelacao(Map<Integer, Integer> freq1, Map<Integer, Integer> freq2) {
        Set<Integer> todosNumeros = new HashSet<>();
        todosNumeros.addAll(freq1.keySet());
        todosNumeros.addAll(freq2.keySet());

        if (todosNumeros.isEmpty()) return 0;

        double[] arr1 = new double[todosNumeros.size()];
        double[] arr2 = new double[todosNumeros.size()];
        int i = 0;
        for (int num : todosNumeros) {
            arr1[i] = freq1.getOrDefault(num, 0);
            arr2[i] = freq2.getOrDefault(num, 0);
            i++;
        }

        double mean1 = Arrays.stream(arr1).average().orElse(0);
        double mean2 = Arrays.stream(arr2).average().orElse(0);

        double num = 0, den1 = 0, den2 = 0;
        for (int j = 0; j < arr1.length; j++) {
            double d1 = arr1[j] - mean1;
            double d2 = arr2[j] - mean2;
            num += d1 * d2;
            den1 += d1 * d1;
            den2 += d2 * d2;
        }

        double den = Math.sqrt(den1 * den2);
        return den > 0 ? Math.round(num / den * 1000.0) / 1000.0 : 0;
    }

    private BigDecimal getPremioFaixaUm(Concurso c) {
        return c.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst()
                .map(FaixaPremiacao::getValorPremio)
                .orElse(null);
    }

    private int getGanhadoresFaixa(Concurso c, int faixa) {
        return c.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == faixa)
                .findFirst()
                .map(f -> f.getNumeroGanhadores() != null ? f.getNumeroGanhadores() : 0)
                .orElse(0);
    }

    private DuplaSenaAnalise criarAnaliseVazia() {
        return new DuplaSenaAnalise(
            0,
            new ComparacaoSorteios(Map.of(), Map.of(), 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(),
            new EstatisticasCoincidencia(0, 0, 0, Map.of())
        );
    }
}
