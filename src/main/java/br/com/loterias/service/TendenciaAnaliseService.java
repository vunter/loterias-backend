package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TendenciaAnaliseService {

    private static final Logger log = LoggerFactory.getLogger(TendenciaAnaliseService.class);

    private final ConcursoRepository concursoRepository;

    public TendenciaAnaliseService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    public record TendenciaResponse(
        TipoLoteria tipo,
        String nomeLoteria,
        int totalConcursosAnalisados,
        List<NumeroTendencia> tendenciasQuentes,
        List<NumeroTendencia> tendenciasFrias,
        List<NumeroTendencia> tendenciasEmergentes,
        Map<String, Double> mediasHistoricas,
        List<PadraoVencedor> padroesVencedores
    ) {}

    public record NumeroTendencia(
        int numero,
        long frequenciaTotal,
        long frequenciaRecente,
        double taxaCrescimento,
        int atrasoAtual,
        String tendencia
    ) {}

    public record PadraoVencedor(
        String padrao,
        String descricao,
        int ocorrencias,
        double percentual,
        List<Integer> exemploConcursos
    ) {}

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'tendencias-' + #tipo.name()")
    public TendenciaResponse analisarTendencias(TipoLoteria tipo) {
        log.info("Analisando tendências para {}", tipo.getNome());

        List<Concurso> concursos = concursoRepository.findByTipoLoteriaWithDezenas(tipo).stream()
            .sorted(Comparator.comparing(Concurso::getNumero))
            .toList();

        if (concursos.isEmpty()) {
            log.info("Nenhum concurso encontrado para análise de tendências: tipo={}", tipo.getNome());
            return new TendenciaResponse(tipo, tipo.getNome(), 0, List.of(), List.of(), List.of(), Map.of(), List.of());
        }

        int total = concursos.size();
        int recenteSize = Math.min(50, total / 5);
        log.debug("Calculando tendências: total={}, periodoRecente={}", total, recenteSize);
        List<Concurso> concursosRecentes = concursos.subList(Math.max(0, total - recenteSize), total);

        Map<Integer, Long> frequenciaTotal = calcularFrequencia(concursos, tipo);
        Map<Integer, Long> frequenciaRecente = calcularFrequencia(concursosRecentes, tipo);
        Map<Integer, Integer> atrasos = calcularAtrasos(concursos, tipo);

        List<NumeroTendencia> todasTendencias = new ArrayList<>();
        for (int num = tipo.getNumeroInicial(); num <= tipo.getNumeroFinal(); num++) {
            long freqTotal = frequenciaTotal.getOrDefault(num, 0L);
            long freqRecente = frequenciaRecente.getOrDefault(num, 0L);
            int atraso = atrasos.getOrDefault(num, total);

            double freqMediaTotal = (double) freqTotal / total;
            double freqMediaRecente = recenteSize > 0 ? (double) freqRecente / recenteSize : 0;
            double taxaCrescimento = freqMediaTotal > 0 ? ((freqMediaRecente - freqMediaTotal) / freqMediaTotal) * 100 : 0;

            String tendencia = classificarTendencia(taxaCrescimento, atraso, recenteSize);

            todasTendencias.add(new NumeroTendencia(num, freqTotal, freqRecente, 
                Math.round(taxaCrescimento * 100.0) / 100.0, atraso, tendencia));
        }

        List<NumeroTendencia> quentes = todasTendencias.stream()
            .filter(t -> t.tendencia().equals("QUENTE"))
            .sorted(Comparator.comparingDouble(NumeroTendencia::taxaCrescimento).reversed())
            .limit(10)
            .toList();

        List<NumeroTendencia> frias = todasTendencias.stream()
            .filter(t -> t.tendencia().equals("FRIO"))
            .sorted(Comparator.comparingDouble(NumeroTendencia::taxaCrescimento))
            .limit(10)
            .toList();

        List<NumeroTendencia> emergentes = todasTendencias.stream()
            .filter(t -> t.tendencia().equals("EMERGENTE"))
            .sorted(Comparator.comparingDouble(NumeroTendencia::taxaCrescimento).reversed())
            .limit(10)
            .toList();

        Map<String, Double> medias = calcularMediasHistoricas(concursos, tipo);
        List<PadraoVencedor> padroes = detectarPadroesVencedores(concursos, tipo);

        log.info("Tendências calculadas: tipo={}, quentes={}, frias={}, emergentes={}", tipo.getNome(), quentes.size(), frias.size(), emergentes.size());

        return new TendenciaResponse(tipo, tipo.getNome(), total, quentes, frias, emergentes, medias, padroes);
    }

    private Map<Integer, Long> calcularFrequencia(List<Concurso> concursos, TipoLoteria tipo) {
        Map<Integer, Long> freq = new HashMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            freq.put(i, 0L);
        }
        for (Concurso c : concursos) {
            for (Integer d : c.getDezenasSorteadas()) {
                freq.merge(d, 1L, Long::sum);
            }
        }
        return freq;
    }

    private Map<Integer, Integer> calcularAtrasos(List<Concurso> concursos, TipoLoteria tipo) {
        if (concursos.isEmpty()) return Map.of();
        
        int ultimoNumero = concursos.get(concursos.size() - 1).getNumero();
        Map<Integer, Integer> ultimaAparicao = new HashMap<>();
        
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            ultimaAparicao.put(i, 0);
        }
        
        for (Concurso c : concursos) {
            for (Integer d : c.getDezenasSorteadas()) {
                ultimaAparicao.put(d, c.getNumero());
            }
        }
        
        Map<Integer, Integer> atrasos = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : ultimaAparicao.entrySet()) {
            atrasos.put(entry.getKey(), ultimoNumero - entry.getValue());
        }
        return atrasos;
    }

    private String classificarTendencia(double taxaCrescimento, int atraso, int periodoRecente) {
        if (taxaCrescimento > 30 && atraso < periodoRecente / 3) {
            return "QUENTE";
        } else if (taxaCrescimento < -30 || atraso > periodoRecente) {
            return "FRIO";
        } else if (taxaCrescimento > 15 && atraso < periodoRecente / 2) {
            return "EMERGENTE";
        } else if (atraso > periodoRecente * 0.7) {
            return "ATRASADO";
        }
        return "ESTAVEL";
    }

    private Map<String, Double> calcularMediasHistoricas(List<Concurso> concursos, TipoLoteria tipo) {
        Map<String, Double> medias = new LinkedHashMap<>();
        
        if (concursos.isEmpty()) return medias;

        double somaMedia = concursos.stream()
            .mapToDouble(c -> c.getDezenasSorteadas().stream().mapToInt(Integer::intValue).sum())
            .average().orElse(0);
        medias.put("somaMedia", Math.round(somaMedia * 100.0) / 100.0);

        double paresMedia = concursos.stream()
            .mapToDouble(c -> c.getDezenasSorteadas().stream().filter(d -> d % 2 == 0).count())
            .average().orElse(0);
        medias.put("paresMedia", Math.round(paresMedia * 100.0) / 100.0);

        double imparesMedia = concursos.stream()
            .mapToDouble(c -> c.getDezenasSorteadas().stream().filter(d -> d % 2 != 0).count())
            .average().orElse(0);
        medias.put("imparesMedia", Math.round(imparesMedia * 100.0) / 100.0);

        int metade = tipo.getNumeroFinal() / 2;
        double baixosMedia = concursos.stream()
            .mapToDouble(c -> c.getDezenasSorteadas().stream().filter(d -> d <= metade).count())
            .average().orElse(0);
        medias.put("baixosMedia", Math.round(baixosMedia * 100.0) / 100.0);

        double altosMedia = concursos.stream()
            .mapToDouble(c -> c.getDezenasSorteadas().stream().filter(d -> d > metade).count())
            .average().orElse(0);
        medias.put("altosMedia", Math.round(altosMedia * 100.0) / 100.0);

        return medias;
    }

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'padroes-' + #tipo.name()")
    public List<PadraoVencedor> detectarPadroesVencedores(List<Concurso> concursos, TipoLoteria tipo) {
        if (concursos.isEmpty()) return List.of();

        List<Concurso> comGanhador = concursos.stream()
            .filter(this::teveGanhadorPrincipal)
            .toList();

        if (comGanhador.isEmpty()) return List.of();

        int totalComGanhador = comGanhador.size();
        List<PadraoVencedor> padroes = new ArrayList<>();

        Map<String, List<Integer>> padraoPares = analisarPadraoPares(comGanhador, totalComGanhador);
        for (Map.Entry<String, List<Integer>> entry : padraoPares.entrySet()) {
            int ocorrencias = entry.getValue().size();
            double percentual = Math.round((ocorrencias * 100.0 / totalComGanhador) * 100.0) / 100.0;
            padroes.add(new PadraoVencedor(
                "PARES_" + entry.getKey(),
                "Concursos com " + entry.getKey() + " números pares",
                ocorrencias,
                percentual,
                entry.getValue().stream().limit(3).toList()
            ));
        }

        Map<String, List<Integer>> padraoSoma = analisarPadraoSoma(comGanhador, tipo, totalComGanhador);
        for (Map.Entry<String, List<Integer>> entry : padraoSoma.entrySet()) {
            int ocorrencias = entry.getValue().size();
            double percentual = Math.round((ocorrencias * 100.0 / totalComGanhador) * 100.0) / 100.0;
            padroes.add(new PadraoVencedor(
                "SOMA_" + entry.getKey(),
                "Concursos com soma " + entry.getKey(),
                ocorrencias,
                percentual,
                entry.getValue().stream().limit(3).toList()
            ));
        }

        Map<String, List<Integer>> padraoSequencial = analisarPadraoSequencial(comGanhador, totalComGanhador);
        for (Map.Entry<String, List<Integer>> entry : padraoSequencial.entrySet()) {
            int ocorrencias = entry.getValue().size();
            double percentual = Math.round((ocorrencias * 100.0 / totalComGanhador) * 100.0) / 100.0;
            padroes.add(new PadraoVencedor(
                "SEQ_" + entry.getKey(),
                entry.getKey() + " números sequenciais",
                ocorrencias,
                percentual,
                entry.getValue().stream().limit(3).toList()
            ));
        }

        return padroes.stream()
            .filter(p -> p.ocorrencias() >= 3)
            .sorted(Comparator.comparingDouble(PadraoVencedor::percentual).reversed())
            .limit(15)
            .toList();
    }

    private boolean teveGanhadorPrincipal(Concurso c) {
        return c.getFaixasPremiacao().stream()
            .filter(f -> f.getFaixa() == 1)
            .anyMatch(f -> f.getNumeroGanhadores() != null && f.getNumeroGanhadores() > 0);
    }

    private Map<String, List<Integer>> analisarPadraoPares(List<Concurso> concursos, int total) {
        Map<String, List<Integer>> resultado = new LinkedHashMap<>();
        
        for (Concurso c : concursos) {
            int pares = (int) c.getDezenasSorteadas().stream().filter(d -> d % 2 == 0).count();
            String key = pares + "P";
            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getNumero());
        }
        
        return resultado.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .limit(5)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, List<Integer>> analisarPadraoSoma(List<Concurso> concursos, TipoLoteria tipo, int total) {
        Map<String, List<Integer>> resultado = new LinkedHashMap<>();
        
        int somaMin = tipo.getMinimoNumeros() * tipo.getNumeroInicial();
        int somaMax = tipo.getMinimoNumeros() * tipo.getNumeroFinal();
        int range = (somaMax - somaMin) / 5;
        
        for (Concurso c : concursos) {
            int soma = c.getDezenasSorteadas().stream().mapToInt(Integer::intValue).sum();
            int faixa = (soma - somaMin) / Math.max(range, 1);
            faixa = Math.min(faixa, 4);
            
            String key = switch (faixa) {
                case 0 -> "BAIXA";
                case 1 -> "MEDIA_BAIXA";
                case 2 -> "MEDIA";
                case 3 -> "MEDIA_ALTA";
                default -> "ALTA";
            };
            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getNumero());
        }
        
        return resultado.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, List<Integer>> analisarPadraoSequencial(List<Concurso> concursos, int total) {
        Map<String, List<Integer>> resultado = new LinkedHashMap<>();
        
        for (Concurso c : concursos) {
            List<Integer> dezenas = new ArrayList<>(c.getDezenasSorteadas());
            Collections.sort(dezenas);
            
            int sequenciais = 0;
            for (int i = 0; i < dezenas.size() - 1; i++) {
                if (dezenas.get(i + 1) - dezenas.get(i) == 1) {
                    sequenciais++;
                }
            }
            
            String key = sequenciais == 0 ? "0" : String.valueOf(sequenciais);
            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getNumero());
        }
        
        return resultado.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public record HistoricoMensal(
        int ano,
        int mes,
        Map<Integer, Long> frequenciaMensal,
        List<Integer> maisFrequentes,
        List<Integer> menosFrequentes
    ) {}

    @Cacheable(value = CacheConfig.CACHE_ESTATISTICAS, key = "'historico-mensal-' + #tipo.name()")
    public List<HistoricoMensal> historicoMensalFrequencia(TipoLoteria tipo) {
        log.info("Calculando histórico mensal de frequência para {}", tipo.getNome());

        List<Concurso> concursos = concursoRepository.findByTipoLoteriaWithDezenas(tipo);

        Map<String, List<Concurso>> porMes = concursos.stream()
            .filter(c -> c.getDataApuracao() != null)
            .collect(Collectors.groupingBy(c -> 
                c.getDataApuracao().getYear() + "-" + String.format("%02d", c.getDataApuracao().getMonthValue())));

        List<HistoricoMensal> historico = new ArrayList<>();
        log.debug("Meses para processar no histórico mensal: count={}", porMes.size());

        for (Map.Entry<String, List<Concurso>> entry : porMes.entrySet()) {
            String[] parts = entry.getKey().split("-");
            int ano = Integer.parseInt(parts[0]);
            int mes = Integer.parseInt(parts[1]);

            Map<Integer, Long> freq = calcularFrequencia(entry.getValue(), tipo);

            List<Integer> maisFreq = freq.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

            List<Integer> menosFreq = freq.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

            historico.add(new HistoricoMensal(ano, mes, freq, maisFreq, menosFreq));
        }

        return historico.stream()
            .sorted(Comparator.comparingInt(HistoricoMensal::ano)
                .thenComparingInt(HistoricoMensal::mes))
            .toList();
    }
}
