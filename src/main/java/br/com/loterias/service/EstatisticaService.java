package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class EstatisticaService {

    private static final Logger log = LoggerFactory.getLogger(EstatisticaService.class);

    private final ConcursoRepository concursoRepository;

    public EstatisticaService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public Map<Integer, Long> numerosMaisFrequentes(TipoLoteria tipo, int quantidade) {
        log.debug("Buscando {} números mais frequentes para {}", quantidade, tipo.getNome());
        Map<Integer, Long> frequencia = frequenciaTodosNumeros(tipo);
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<Integer, Long> numerosMenosFrequentes(TipoLoteria tipo, int quantidade) {
        log.debug("Buscando {} números menos frequentes para {}", quantidade, tipo.getNome());
        Map<Integer, Long> frequencia = frequenciaTodosNumeros(tipo);
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'frequencia-' + #tipo.name()")
    public Map<Integer, Long> frequenciaTodosNumeros(TipoLoteria tipo) {
        log.debug("Calculando frequência de todos os números para {}", tipo.getNome());

        Map<Integer, Long> frequencia = new TreeMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            frequencia.put(i, 0L);
        }

        // Use native aggregate query instead of loading all entities
        List<Object[]> resultados = concursoRepository.findFrequenciaDezenas(tipo.name());
        for (Object[] row : resultados) {
            Integer dezena = ((Number) row[0]).intValue();
            Long freq = ((Number) row[1]).longValue();
            frequencia.put(dezena, freq);
        }

        return frequencia;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'freq-ganhadores-mais-' + #tipo.name() + '-' + #quantidade")
    public Map<Integer, Long> numerosMaisFrequentesComGanhadores(TipoLoteria tipo, int quantidade) {
        log.debug("Buscando {} números mais frequentes em concursos com ganhadores para {}", quantidade, tipo.getNome());
        Map<Integer, Long> frequencia = frequenciaComGanhadores(tipo);
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'freq-ganhadores-menos-' + #tipo.name() + '-' + #quantidade")
    public Map<Integer, Long> numerosMenosFrequentesComGanhadores(TipoLoteria tipo, int quantidade) {
        log.debug("Buscando {} números menos frequentes em concursos com ganhadores para {}", quantidade, tipo.getNome());
        Map<Integer, Long> frequencia = frequenciaComGanhadores(tipo);
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    private Map<Integer, Long> frequenciaComGanhadores(TipoLoteria tipo) {
        Map<Integer, Long> frequencia = new TreeMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            frequencia.put(i, 0L);
        }

        List<Object[]> freqData = concursoRepository.findFrequenciaDezenasComGanhadores(tipo.name());
        for (Object[] row : freqData) {
            frequencia.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        return frequencia;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'pares-impares-' + #tipo.name()")
    public Map<String, Double> paresImpares(TipoLoteria tipo) {
        log.debug("Calculando média de pares/ímpares por concurso para {}", tipo.getNome());

        Object[] result = concursoRepository.findMediaParesImpares(tipo.name());
        if (result == null || result[0] == null) {
            return Map.of("mediaPares", 0.0, "mediaImpares", 0.0);
        }

        Map<String, Double> resultado = new LinkedHashMap<>();
        resultado.put("mediaPares", ((Number) result[0]).doubleValue());
        resultado.put("mediaImpares", ((Number) result[1]).doubleValue());
        return resultado;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'soma-media-' + #tipo.name()")
    public Double somaMedia(TipoLoteria tipo) {
        log.debug("Calculando soma média das dezenas para {}", tipo.getNome());
        Double result = concursoRepository.findSomaMedia(tipo.name());
        return result != null ? result : 0.0;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'atrasados-' + #tipo.name() + '-' + #quantidade")
    public Map<Integer, Long> numerosAtrasados(TipoLoteria tipo, int quantidade) {
        log.debug("Buscando {} números mais atrasados para {}", quantidade, tipo.getNome());

        Optional<Integer> maxNumero = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        if (maxNumero.isEmpty()) {
            return Collections.emptyMap();
        }
        int ultimoConcurso = maxNumero.get();

        // Use native aggregate query instead of loading all entities
        Map<Integer, Integer> ultimaAparicao = new HashMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            ultimaAparicao.put(i, 0);
        }

        List<Object[]> resultados = concursoRepository.findUltimaAparicaoDezenas(tipo.name());
        for (Object[] row : resultados) {
            Integer dezena = ((Number) row[0]).intValue();
            Integer ultimo = ((Number) row[1]).intValue();
            ultimaAparicao.put(dezena, ultimo);
        }

        Map<Integer, Long> atraso = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : ultimaAparicao.entrySet()) {
            long concursosAtrasados = ultimoConcurso - entry.getValue();
            atraso.put(entry.getKey(), concursosAtrasados);
        }

        return atraso.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'sequenciais-' + #tipo.name()")
    public Map<String, Long> combinacoesSequenciais(TipoLoteria tipo) {
        log.debug("Calculando frequência de combinações sequenciais para {}", tipo.getNome());
        List<Concurso> concursos = buscarConcursosPorTipo(tipo);

        Map<String, Long> sequenciais = new TreeMap<>();

        for (Concurso concurso : concursos) {
            List<Integer> dezenas = new ArrayList<>(concurso.getDezenasSorteadas());
            Collections.sort(dezenas);

            for (int i = 0; i < dezenas.size() - 1; i++) {
                if (dezenas.get(i + 1) - dezenas.get(i) == 1) {
                    String par = String.format("%02d-%02d", dezenas.get(i), dezenas.get(i + 1));
                    sequenciais.merge(par, 1L, Long::sum);
                }
            }
        }

        return sequenciais.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'faixas-' + #tipo.name()")
    public Map<String, Long> dezenasPorFaixa(TipoLoteria tipo) {
        log.debug("Calculando distribuição por faixas para {}", tipo.getNome());
        List<Concurso> concursos = buscarConcursosPorTipo(tipo);

        Map<String, Long> distribuicao = new LinkedHashMap<>();

        int maxNumero = tipo.getNumerosDezenas();
        int numFaixas = (int) Math.ceil(maxNumero / 10.0);

        for (int i = 0; i < numFaixas; i++) {
            int inicio = i * 10 + 1;
            int fim = Math.min((i + 1) * 10, maxNumero);
            String faixa = String.format("%02d-%02d", inicio, fim);
            distribuicao.put(faixa, 0L);
        }

        for (Concurso concurso : concursos) {
            for (Integer dezena : concurso.getDezenasSorteadas()) {
                int faixaIndex = (dezena - 1) / 10;
                int inicio = faixaIndex * 10 + 1;
                int fim = Math.min((faixaIndex + 1) * 10, maxNumero);
                String faixa = String.format("%02d-%02d", inicio, fim);
                distribuicao.merge(faixa, 1L, Long::sum);
            }
        }

        return distribuicao;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'historico-' + #tipo.name() + '-' + #numero")
    public List<Integer> historicoNumero(TipoLoteria tipo, Integer numero) {
        log.debug("Buscando histórico do número {} para {}", numero, tipo.getNome());
        return concursoRepository.findConcursosComDezena(tipo.name(), numero);
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'correlacao-' + #tipo.name() + '-' + #quantidade")
    public Map<String, Long> correlacaoNumeros(TipoLoteria tipo, int quantidade) {
        log.debug("Calculando correlação de {} pares mais frequentes para {}", quantidade, tipo.getNome());
        List<Concurso> concursos = buscarConcursosPorTipo(tipo);

        Map<String, Long> frequenciaPares = new HashMap<>();

        for (Concurso concurso : concursos) {
            List<Integer> dezenas = new ArrayList<>(concurso.getDezenasSorteadas());
            Collections.sort(dezenas);

            for (int i = 0; i < dezenas.size(); i++) {
                for (int j = i + 1; j < dezenas.size(); j++) {
                    String par = String.format("%02d-%02d", dezenas.get(i), dezenas.get(j));
                    frequenciaPares.merge(par, 1L, Long::sum);
                }
            }
        }

        return frequenciaPares.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'acompanham-' + #tipo.name() + '-' + #numero + '-' + #quantidade")
    public Map<Integer, Long> numerosQueAcompanham(TipoLoteria tipo, Integer numero, int quantidade) {
        log.debug("Buscando {} números que mais acompanham o {} para {}", quantidade, numero, tipo.getNome());
        List<Concurso> concursos = buscarConcursosPorTipo(tipo);

        Map<Integer, Long> frequenciaAcompanhantes = new HashMap<>();

        for (Concurso concurso : concursos) {
            List<Integer> dezenas = concurso.getDezenasSorteadas();
            if (dezenas.contains(numero)) {
                for (Integer dezena : dezenas) {
                    if (!dezena.equals(numero)) {
                        frequenciaAcompanhantes.merge(dezena, 1L, Long::sum);
                    }
                }
            }
        }

        return frequenciaAcompanhantes.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(quantidade)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, _) -> e1,
                        LinkedHashMap::new
                ));
    }

    private List<Concurso> buscarConcursosPorTipo(TipoLoteria tipo) {
        return concursoRepository.findByTipoLoteriaOnlyDezenas(tipo);
    }
}
