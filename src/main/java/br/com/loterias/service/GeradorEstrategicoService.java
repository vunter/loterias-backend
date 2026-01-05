package br.com.loterias.service;

import br.com.loterias.domain.dto.EstrategiaGeracao;
import br.com.loterias.domain.dto.GerarJogoResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeradorEstrategicoService {

    private static final Logger log = LoggerFactory.getLogger(GeradorEstrategicoService.class);

    private static final int MAX_JOGOS = 10;
    private static final int MAX_TENTATIVAS = 1000;

    private final ConcursoRepository concursoRepository;
    private final TimeCoracaoMesSorteService timeCoracaoMesSorteService;
    private final java.security.SecureRandom random;

    public GeradorEstrategicoService(ConcursoRepository concursoRepository, TimeCoracaoMesSorteService timeCoracaoMesSorteService) {
        this.concursoRepository = concursoRepository;
        this.timeCoracaoMesSorteService = timeCoracaoMesSorteService;
        this.random = new java.security.SecureRandom();
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, EstrategiaGeracao estrategia, int quantidadeJogos) {
        return gerarJogos(tipo, estrategia, quantidadeJogos, null, null, List.of(), false);
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, EstrategiaGeracao estrategia, int quantidadeJogos, boolean debug) {
        return gerarJogos(tipo, estrategia, quantidadeJogos, null, null, List.of(), debug);
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, EstrategiaGeracao estrategia, int quantidadeJogos, Integer quantidadeNumeros, boolean debug) {
        return gerarJogos(tipo, estrategia, quantidadeJogos, quantidadeNumeros, null, List.of(), debug);
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, EstrategiaGeracao estrategia, int quantidadeJogos, Integer quantidadeNumeros, Integer quantidadeTrevos, List<Integer> trevosFixos, boolean debug) {
        int qtdNumeros = validarQuantidadeNumeros(tipo, quantidadeNumeros);
        int qtdTrevos = validarQuantidadeTrevos(quantidadeTrevos, trevosFixos);
        log.info("Gerando {} jogos para {} com estratégia {}, {} números, {} trevos (debug: {})", quantidadeJogos, tipo.getNome(), estrategia.getNome(), qtdNumeros, qtdTrevos, debug);

        int qtdJogos = Math.min(Math.max(quantidadeJogos, 1), MAX_JOGOS);
        List<String> etapas = debug ? new ArrayList<>() : null;

        if (debug) {
            etapas.add("Iniciando geração de " + qtdJogos + " jogos com " + qtdNumeros + " números para " + tipo.getNome());
            etapas.add("Estratégia selecionada: " + estrategia.getNome() + " - " + estrategia.getDescricao());
            if (qtdNumeros > tipo.getMinimoNumeros()) {
                etapas.add("Jogo ampliado: " + qtdNumeros + " números (mínimo: " + tipo.getMinimoNumeros() + ", máximo: " + tipo.getMaximoNumeros() + ")");
            }
            if (tipo == TipoLoteria.MAIS_MILIONARIA && qtdTrevos > 2) {
                etapas.add("Trevos ampliados: " + qtdTrevos + " trevos (mínimo: 2, máximo: 6)");
            }
            if (trevosFixos != null && !trevosFixos.isEmpty()) {
                etapas.add("Trevos fixos: " + trevosFixos);
            }
        }

        if (tipo == TipoLoteria.MAIS_MILIONARIA) {
            return gerarJogosMaisMilionaria(estrategia, qtdJogos, qtdNumeros, qtdTrevos, trevosFixos, debug, etapas);
        }

        Map<Integer, Long> frequencias = debug ? buscarFrequenciaDezenas(tipo) : null;
        Map<Integer, Long> atrasos = debug ? buscarAtrasosDezenas(tipo) : null;

        if (debug && frequencias != null) {
            etapas.add("Frequências carregadas para " + frequencias.size() + " números");
            List<Integer> topFreq = frequencias.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();
            etapas.add("Top 10 mais frequentes: " + topFreq);
        }

        if (debug && atrasos != null) {
            List<Integer> topAtrasos = atrasos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();
            etapas.add("Top 10 mais atrasados: " + topAtrasos);
        }

        Map<Integer, Double> pesos = calcularPesosPorEstrategia(tipo, estrategia);
        
        if (debug) {
            etapas.add("Calculando pesos para cada número baseado na estratégia...");
            List<Map.Entry<Integer, Double>> topPesos = pesos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(10)
                    .toList();
            etapas.add("Top 10 números com maior peso: " + topPesos.stream()
                    .map(e -> e.getKey() + "(peso=" + String.format("%.2f", e.getValue()) + ")")
                    .collect(Collectors.joining(", ")));
        }

        List<List<Integer>> jogos = gerarJogosComPesos(tipo, pesos, qtdJogos, qtdNumeros, estrategia);

        if (debug) {
            etapas.add("Jogos gerados com sucesso: " + jogos.size());
            for (int i = 0; i < jogos.size(); i++) {
                etapas.add("Jogo " + (i + 1) + ": " + jogos.get(i));
            }
        }

        // Gerar sugestões de time/mês para Timemania e Dia de Sorte (um por jogo)
        String timeSugerido = null;
        String mesSugerido = null;
        List<String> timesSugeridos = null;
        List<String> mesesSugeridos = null;
        List<String> timesTop5 = null;
        List<String> mesesTop5 = null;
        GerarJogoResponse.TimeCoracaoDebug timeCoracaoDebug = null;

        // Mapear estratégia de números para estratégia de time/mês
        String estrategiaTimeCoracao = mapearEstrategiaParaTimeCoracao(estrategia);

        if (tipo == TipoLoteria.TIMEMANIA) {
            try {
                List<TimeCoracaoMesSorteService.SugestaoDetalhada> sugestoes = 
                        timeCoracaoMesSorteService.sugerirMultiplos(tipo, estrategiaTimeCoracao, jogos.size());
                timesSugeridos = sugestoes.stream().map(s -> s.sugestao()).toList();
                timeSugerido = timesSugeridos.isEmpty() ? null : timesSugeridos.get(0);
                
                if (debug && !sugestoes.isEmpty()) {
                    TimeCoracaoMesSorteService.SugestaoDetalhada primeiraSugestao = sugestoes.get(0);
                    timesTop5 = primeiraSugestao.ranking().stream().map(r -> r.nome()).limit(5).toList();
                    etapas.add("─── Análise do Time do Coração ───");
                    etapas.add("Estratégia aplicada: " + primeiraSugestao.estrategia().toUpperCase());
                    etapas.add("Times selecionados (" + timesSugeridos.size() + " jogos): " + timesSugeridos);
                    for (int i = 0; i < sugestoes.size(); i++) {
                        TimeCoracaoMesSorteService.SugestaoDetalhada s = sugestoes.get(i);
                        etapas.add("  Jogo " + (i + 1) + ": " + s.sugestao() + " (" + s.frequencia() + " aparições, " + String.format("%.2f", s.percentual()) + "%)");
                    }
                    
                    timeCoracaoDebug = new GerarJogoResponse.TimeCoracaoDebug(
                            "TIME_CORACAO",
                            primeiraSugestao.estrategia(),
                            String.join(", ", timesSugeridos),
                            "Sugeridos " + timesSugeridos.size() + " times distintos",
                            primeiraSugestao.frequencia(),
                            primeiraSugestao.percentual(),
                            primeiraSugestao.atrasoAtual(),
                            primeiraSugestao.ranking().stream()
                                    .map(r -> new GerarJogoResponse.TimeCoracaoDebug.ItemRanking(r.nome(), r.frequencia(), r.percentual(), r.atraso()))
                                    .toList()
                    );
                }
            } catch (Exception e) {
                log.warn("Erro ao sugerir times para Timemania: {}", e.getMessage());
            }
        }

        if (tipo == TipoLoteria.DIA_DE_SORTE) {
            try {
                List<TimeCoracaoMesSorteService.SugestaoDetalhada> sugestoes = 
                        timeCoracaoMesSorteService.sugerirMultiplos(tipo, estrategiaTimeCoracao, jogos.size());
                mesesSugeridos = sugestoes.stream().map(s -> s.sugestao()).toList();
                mesSugerido = mesesSugeridos.isEmpty() ? null : mesesSugeridos.get(0);
                
                if (debug && !sugestoes.isEmpty()) {
                    TimeCoracaoMesSorteService.SugestaoDetalhada primeiraSugestao = sugestoes.get(0);
                    mesesTop5 = primeiraSugestao.ranking().stream().map(r -> r.nome()).limit(5).toList();
                    etapas.add("─── Análise do Mês da Sorte ───");
                    etapas.add("Estratégia aplicada: " + primeiraSugestao.estrategia().toUpperCase());
                    etapas.add("Meses selecionados (" + mesesSugeridos.size() + " jogos): " + mesesSugeridos);
                    for (int i = 0; i < sugestoes.size(); i++) {
                        TimeCoracaoMesSorteService.SugestaoDetalhada s = sugestoes.get(i);
                        etapas.add("  Jogo " + (i + 1) + ": " + s.sugestao() + " (" + s.frequencia() + " aparições, " + String.format("%.2f", s.percentual()) + "%)");
                    }
                    
                    timeCoracaoDebug = new GerarJogoResponse.TimeCoracaoDebug(
                            "MES_SORTE",
                            primeiraSugestao.estrategia(),
                            String.join(", ", mesesSugeridos),
                            "Sugeridos " + mesesSugeridos.size() + " meses distintos",
                            primeiraSugestao.frequencia(),
                            primeiraSugestao.percentual(),
                            primeiraSugestao.atrasoAtual(),
                            primeiraSugestao.ranking().stream()
                                    .map(r -> new GerarJogoResponse.TimeCoracaoDebug.ItemRanking(r.nome(), r.frequencia(), r.percentual(), r.atraso()))
                                    .toList()
                    );
                }
            } catch (Exception e) {
                log.warn("Erro ao sugerir meses para Dia de Sorte: {}", e.getMessage());
            }
        }

        GerarJogoResponse.DebugInfo debugInfo = null;
        if (debug) {
            List<Integer> quentes = frequencias != null ? frequencias.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList() : List.of();
            List<Integer> frios = frequencias != null ? frequencias.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(10).map(Map.Entry::getKey).toList() : List.of();
            List<Integer> atrasadosList = atrasos != null ? atrasos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList() : List.of();

            debugInfo = new GerarJogoResponse.DebugInfo(
                    etapas,
                    new TreeMap<>(pesos),
                    frequencias,
                    atrasos,
                    quentes,
                    frios,
                    atrasadosList,
                    estrategia.getDescricao(),
                    timesTop5,
                    mesesTop5,
                    timeCoracaoDebug
            );
        }

        return new GerarJogoResponse(tipo, jogos, estrategia.getNome() + ": " + estrategia.getDescricao(), LocalDateTime.now(), timeSugerido, mesSugerido, timesSugeridos, mesesSugeridos, null, debugInfo);
    }

    private String mapearEstrategiaParaTimeCoracao(EstrategiaGeracao estrategia) {
        return switch (estrategia) {
            case NUMEROS_QUENTES, COMBINADO, TENDENCIA_RECENTE, NUMEROS_PREMIADOS -> "quente";
            case NUMEROS_FRIOS -> "frio";
            case NUMEROS_ATRASADOS -> "atrasado";
            case ALEATORIO, EQUILIBRADO, DISTRIBUICAO_FAIXAS, PARES_FREQUENTES -> "aleatorio";
        };
    }

    private Map<Integer, Double> calcularPesosPorEstrategia(TipoLoteria tipo, EstrategiaGeracao estrategia) {
        Map<Integer, Double> pesos = new HashMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            pesos.put(i, 1.0);
        }

        if (estrategia == EstrategiaGeracao.ALEATORIO || estrategia == EstrategiaGeracao.DISTRIBUICAO_FAIXAS) {
            return pesos;
        }

        try {
            switch (estrategia) {
                case NUMEROS_QUENTES, COMBINADO, TENDENCIA_RECENTE -> {
                    Map<Integer, Long> frequencia = buscarFrequenciaDezenas(tipo);
                    long maxFreq = frequencia.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    for (Map.Entry<Integer, Long> entry : frequencia.entrySet()) {
                        double peso = 1.0 + (4.0 * entry.getValue() / maxFreq);
                        pesos.put(entry.getKey(), peso);
                    }
                }
                case NUMEROS_FRIOS -> {
                    Map<Integer, Long> frequencia = buscarFrequenciaDezenas(tipo);
                    long minFreq = frequencia.values().stream().mapToLong(Long::longValue).min().orElse(0);
                    long maxFreq = frequencia.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    long range = Math.max(maxFreq - minFreq, 1);
                    for (Map.Entry<Integer, Long> entry : frequencia.entrySet()) {
                        double peso = 1.0 + (4.0 * (maxFreq - entry.getValue()) / range);
                        pesos.put(entry.getKey(), peso);
                    }
                }
                case NUMEROS_ATRASADOS -> {
                    Map<Integer, Long> atrasos = buscarAtrasosDezenas(tipo);
                    long maxAtraso = atrasos.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    for (Map.Entry<Integer, Long> entry : atrasos.entrySet()) {
                        double peso = 1.0 + (4.0 * entry.getValue() / Math.max(maxAtraso, 1));
                        pesos.put(entry.getKey(), peso);
                    }
                }
                case NUMEROS_PREMIADOS, EQUILIBRADO, PARES_FREQUENTES -> {
                    Map<Integer, Long> frequencia = buscarFrequenciaDezenas(tipo);
                    long maxFreq = frequencia.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    for (Map.Entry<Integer, Long> entry : frequencia.entrySet()) {
                        double peso = 1.0 + (2.0 * entry.getValue() / maxFreq);
                        pesos.put(entry.getKey(), peso);
                    }
                }
                default -> {}
            }
        } catch (Exception e) {
            log.warn("Erro ao calcular pesos para {}: {}", estrategia, e.getMessage());
        }

        return pesos;
    }

    private Map<Integer, Long> buscarFrequenciaDezenas(TipoLoteria tipo) {
        Map<Integer, Long> frequencia = new TreeMap<>();
        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            frequencia.put(i, 0L);
        }

        List<Object[]> resultados = concursoRepository.findFrequenciaDezenas(tipo.name());
        for (Object[] row : resultados) {
            Integer dezena = ((Number) row[0]).intValue();
            Long freq = ((Number) row[1]).longValue();
            if (dezena >= tipo.getNumeroInicial() && dezena <= tipo.getNumeroFinal()) {
                frequencia.put(dezena, freq);
            }
        }

        return frequencia;
    }

    private Map<Integer, Long> buscarAtrasosDezenas(TipoLoteria tipo) {
        Map<Integer, Long> atrasos = new TreeMap<>();
        
        Optional<Integer> maxNumeroOpt = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        int ultimoConcurso = maxNumeroOpt.orElse(0);

        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            atrasos.put(i, (long) ultimoConcurso);
        }

        List<Object[]> resultados = concursoRepository.findUltimaAparicaoDezenas(tipo.name());
        for (Object[] row : resultados) {
            Integer dezena = ((Number) row[0]).intValue();
            Integer ultimaAparicao = ((Number) row[1]).intValue();
            if (dezena >= tipo.getNumeroInicial() && dezena <= tipo.getNumeroFinal()) {
                atrasos.put(dezena, (long) (ultimoConcurso - ultimaAparicao));
            }
        }

        return atrasos;
    }

    private int validarQuantidadeNumeros(TipoLoteria tipo, Integer quantidadeNumeros) {
        return GeradorValidation.validarQuantidadeNumeros(tipo, quantidadeNumeros);
    }

    private int validarQuantidadeTrevos(Integer quantidadeTrevos, List<Integer> trevosFixos) {
        return GeradorValidation.validarQuantidadeTrevos(quantidadeTrevos, trevosFixos);
    }

    private List<List<Integer>> gerarJogosComPesos(TipoLoteria tipo, Map<Integer, Double> pesos, int quantidade, int qtdNumeros, EstrategiaGeracao estrategia) {
        List<List<Integer>> jogos = new ArrayList<>();
        Set<String> jogosGerados = new HashSet<>();
        int tentativas = 0;

        while (jogos.size() < quantidade && tentativas < MAX_TENTATIVAS) {
            tentativas++;
            List<Integer> jogo;

            if (estrategia == EstrategiaGeracao.DISTRIBUICAO_FAIXAS && tipo != TipoLoteria.SUPER_SETE) {
                jogo = gerarJogoDistribuido(tipo, qtdNumeros);
            } else {
                jogo = gerarJogoComPesos(tipo, pesos, qtdNumeros);
            }

            if (jogo != null && jogo.size() == qtdNumeros) {
                String jogoKey = jogo.toString();
                if (!jogosGerados.contains(jogoKey)) {
                    jogosGerados.add(jogoKey);
                    jogos.add(jogo);
                }
            }
        }

        return jogos;
    }

    private List<Integer> gerarJogoComPesos(TipoLoteria tipo, Map<Integer, Double> pesos, int qtdNumeros) {
        // Column-based lottery: allow duplicates, preserve column order
        if (tipo == TipoLoteria.SUPER_SETE) {
            List<Integer> jogo = new ArrayList<>();
            List<Integer> numeros = new ArrayList<>(pesos.keySet());
            List<Double> pesosList = numeros.stream().map(pesos::get).collect(Collectors.toList());
            while (jogo.size() < qtdNumeros) {
                Integer numero = selecionarNumeroPonderadoSemExclusao(numeros, pesosList);
                if (numero == null) break;
                jogo.add(numero);
            }
            return jogo;
        }

        Set<Integer> jogo = new HashSet<>();
        List<Integer> numeros = new ArrayList<>(pesos.keySet());
        List<Double> pesosList = numeros.stream().map(pesos::get).collect(Collectors.toList());

        while (jogo.size() < qtdNumeros && !numeros.isEmpty()) {
            Integer numero = selecionarNumeroPonderado(numeros, pesosList, jogo);
            if (numero != null) {
                jogo.add(numero);
            } else {
                break;
            }
        }

        List<Integer> resultado = new ArrayList<>(jogo);
        Collections.sort(resultado);
        return resultado;
    }

    private List<Integer> gerarJogoDistribuido(TipoLoteria tipo, int qtdNumeros) {
        Set<Integer> jogo = new HashSet<>();
        int numeroInicial = tipo.getNumeroInicial();
        int numeroFinal = tipo.getNumeroFinal();
        int totalNumeros = tipo.getNumerosDezenas();
        int numFaixas = (int) Math.ceil(totalNumeros / 10.0);

        int[] numerosPorFaixa = new int[numFaixas];
        int base = qtdNumeros / numFaixas;
        int resto = qtdNumeros % numFaixas;
        for (int i = 0; i < numFaixas; i++) {
            numerosPorFaixa[i] = base + (i < resto ? 1 : 0);
        }

        for (int i = 0; i < numFaixas; i++) {
            int inicio = numeroInicial + i * 10;
            int fim = Math.min(numeroInicial + (i + 1) * 10 - 1, numeroFinal);
            List<Integer> faixaNumeros = new ArrayList<>();
            for (int n = inicio; n <= fim; n++) {
                faixaNumeros.add(n);
            }
            Collections.shuffle(faixaNumeros, random);

            int count = 0;
            for (Integer num : faixaNumeros) {
                if (count >= numerosPorFaixa[i]) break;
                jogo.add(num);
                count++;
            }
        }

        while (jogo.size() < qtdNumeros) {
            jogo.add(numeroInicial + random.nextInt(totalNumeros));
        }

        List<Integer> resultado = new ArrayList<>(jogo);
        Collections.sort(resultado);
        return resultado;
    }

    private Integer selecionarNumeroPonderadoSemExclusao(List<Integer> numeros, List<Double> pesos) {
        if (numeros.isEmpty()) return null;
        double totalPeso = 0;
        for (Double p : pesos) totalPeso += p;
        if (totalPeso <= 0) return null;
        double valorAleatorio = random.nextDouble() * totalPeso;
        double acumulado = 0;
        for (int i = 0; i < numeros.size(); i++) {
            acumulado += pesos.get(i);
            if (valorAleatorio <= acumulado) return numeros.get(i);
        }
        return numeros.get(numeros.size() - 1);
    }

    private Integer selecionarNumeroPonderado(List<Integer> numeros, List<Double> pesos, Set<Integer> jaEscolhidos) {
        List<Integer> candidatos = new ArrayList<>();
        List<Double> pesosCandidatos = new ArrayList<>();

        for (int i = 0; i < numeros.size(); i++) {
            if (!jaEscolhidos.contains(numeros.get(i))) {
                candidatos.add(numeros.get(i));
                pesosCandidatos.add(pesos.get(i));
            }
        }

        if (candidatos.isEmpty()) {
            return null;
        }

        double somaTotal = pesosCandidatos.stream().mapToDouble(Double::doubleValue).sum();
        double valorAleatorio = random.nextDouble() * somaTotal;

        double acumulado = 0;
        for (int i = 0; i < candidatos.size(); i++) {
            acumulado += pesosCandidatos.get(i);
            if (valorAleatorio <= acumulado) {
                return candidatos.get(i);
            }
        }

        return candidatos.get(candidatos.size() - 1);
    }

    private GerarJogoResponse gerarJogosMaisMilionaria(EstrategiaGeracao estrategia, int qtdJogos, int qtdNumeros, int qtdTrevos, List<Integer> trevosFixos, boolean debug, List<String> etapas) {
        Map<Integer, Double> pesosDezenas = new HashMap<>();
        Map<Integer, Double> pesosTrevos = new HashMap<>();

        for (int i = 1; i <= 50; i++) pesosDezenas.put(i, 1.0);
        for (int i = 1; i <= 6; i++) pesosTrevos.put(i, 1.0);

        int qtdDezenas = Math.max(6, Math.min(qtdNumeros, 12));

        if (debug && etapas != null) {
            etapas.add("+Milionária: Gerando " + qtdDezenas + " dezenas (1-50) + " + qtdTrevos + " trevos (1-6)");
            if (trevosFixos != null && !trevosFixos.isEmpty()) {
                etapas.add("Trevos fixos incluídos: " + trevosFixos);
            }
        }

        Map<Integer, Long> frequencia = null;
        if (estrategia != EstrategiaGeracao.ALEATORIO) {
            try {
                frequencia = buscarFrequenciaDezenas(TipoLoteria.MAIS_MILIONARIA);
                
                Map<Integer, Long> freqDezenas = new TreeMap<>();
                Map<Integer, Long> freqTrevos = new TreeMap<>();
                
                for (Map.Entry<Integer, Long> e : frequencia.entrySet()) {
                    if (e.getKey() <= 50) {
                        freqDezenas.put(e.getKey(), e.getValue());
                    } else if (e.getKey() <= 56) {
                        freqTrevos.put(e.getKey() - 50, e.getValue());
                    }
                }

                long maxFreqD = freqDezenas.values().stream().mapToLong(Long::longValue).max().orElse(1);
                long maxFreqT = freqTrevos.values().stream().mapToLong(Long::longValue).max().orElse(1);

                if (debug && etapas != null) {
                    etapas.add("Frequência máxima dezenas: " + maxFreqD + ", trevos: " + maxFreqT);
                }

                if (estrategia == EstrategiaGeracao.NUMEROS_QUENTES || estrategia == EstrategiaGeracao.COMBINADO) {
                    for (Map.Entry<Integer, Long> e : freqDezenas.entrySet()) {
                        pesosDezenas.put(e.getKey(), 1.0 + (3.0 * e.getValue() / Math.max(maxFreqD, 1)));
                    }
                    for (Map.Entry<Integer, Long> e : freqTrevos.entrySet()) {
                        pesosTrevos.put(e.getKey(), 1.0 + (3.0 * e.getValue() / Math.max(maxFreqT, 1)));
                    }
                    if (debug && etapas != null) {
                        etapas.add("Pesos aplicados priorizando números quentes");
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao calcular pesos para Mais Milionária: {}", e.getMessage());
            }
        }

        List<List<Integer>> jogos = new ArrayList<>();
        Set<String> jogosGerados = new HashSet<>();
        int tentativas = 0;

        while (jogos.size() < qtdJogos && tentativas < MAX_TENTATIVAS) {
            tentativas++;

            List<Integer> dezenas = selecionarNumerosPonderados(pesosDezenas, qtdDezenas);
            
            // Gerar trevos considerando os trevos fixos
            List<Integer> trevos;
            if (trevosFixos != null && !trevosFixos.isEmpty()) {
                // Começar com os trevos fixos
                trevos = new ArrayList<>(trevosFixos);
                
                // Completar com trevos aleatórios se necessário
                if (trevos.size() < qtdTrevos) {
                    // Remover trevos fixos do mapa de pesos para não duplicar
                    Map<Integer, Double> pesosTrevosDisponiveis = new HashMap<>(pesosTrevos);
                    for (Integer fixo : trevosFixos) {
                        pesosTrevosDisponiveis.remove(fixo);
                    }
                    
                    int trevosRestantes = qtdTrevos - trevos.size();
                    List<Integer> trevosExtras = selecionarNumerosPonderados(pesosTrevosDisponiveis, trevosRestantes);
                    trevos.addAll(trevosExtras);
                }
            } else {
                trevos = selecionarNumerosPonderados(pesosTrevos, qtdTrevos);
            }

            Collections.sort(dezenas);
            Collections.sort(trevos);

            List<Integer> jogo = new ArrayList<>(dezenas);
            jogo.addAll(trevos);

            String jogoKey = jogo.toString();
            if (!jogosGerados.contains(jogoKey)) {
                jogosGerados.add(jogoKey);
                jogos.add(jogo);
            }
        }

        if (debug && etapas != null) {
            etapas.add("Jogos gerados após " + tentativas + " tentativas");
            for (int i = 0; i < jogos.size(); i++) {
                List<Integer> jogo = jogos.get(i);
                etapas.add("Jogo " + (i + 1) + ": Dezenas " + jogo.subList(0, qtdDezenas) + " + Trevos " + jogo.subList(qtdDezenas, qtdDezenas + qtdTrevos));
            }
        }

        GerarJogoResponse.DebugInfo debugInfo = null;
        if (debug) {
            Map<Integer, Double> todosOsPesos = new TreeMap<>(pesosDezenas);
            pesosTrevos.forEach((k, v) -> todosOsPesos.put(k + 100, v));
            
            debugInfo = new GerarJogoResponse.DebugInfo(
                    etapas,
                    todosOsPesos,
                    frequencia,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    estrategia.getDescricao()
            );
        }

        return new GerarJogoResponse(TipoLoteria.MAIS_MILIONARIA, jogos,
                estrategia.getNome() + " (" + qtdDezenas + " dezenas + " + qtdTrevos + " trevos)", LocalDateTime.now(),
                null, null, null, null, qtdDezenas, debugInfo);
    }

    private List<Integer> selecionarNumerosPonderados(Map<Integer, Double> pesos, int quantidade) {
        Set<Integer> selecionados = new HashSet<>();
        List<Integer> numeros = new ArrayList<>(pesos.keySet());
        List<Double> pesosList = numeros.stream().map(pesos::get).collect(Collectors.toList());

        while (selecionados.size() < quantidade) {
            Integer num = selecionarNumeroPonderado(numeros, pesosList, selecionados);
            if (num != null) {
                selecionados.add(num);
            } else {
                break;
            }
        }

        return new ArrayList<>(selecionados);
    }
}
