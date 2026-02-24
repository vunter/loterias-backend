package br.com.loterias.service;

import br.com.loterias.domain.dto.GerarJogoRequest;
import br.com.loterias.domain.dto.GerarJogoResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeradorJogosService {

    private static final Logger log = LoggerFactory.getLogger(GeradorJogosService.class);

    private static final int MAX_JOGOS = 10;
    private static final int MAX_TENTATIVAS = 1000;

    private final EstatisticaService estatisticaService;
    private final TimeCoracaoMesSorteService timeCoracaoMesSorteService;
    private final java.security.SecureRandom random;

    public GeradorJogosService(EstatisticaService estatisticaService, TimeCoracaoMesSorteService timeCoracaoMesSorteService) {
        this.estatisticaService = estatisticaService;
        this.timeCoracaoMesSorteService = timeCoracaoMesSorteService;
        this.random = new java.security.SecureRandom();
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, GerarJogoRequest request) {
        return gerarJogos(tipo, request, false);
    }

    public GerarJogoResponse gerarJogos(TipoLoteria tipo, GerarJogoRequest request, boolean debug) {
        log.info("Gerando jogos para {} com request: {} (debug: {})", tipo.getNome(), request, debug);

        if (tipo == TipoLoteria.MAIS_MILIONARIA) {
            return gerarJogosMaisMilionaria(request, debug);
        }

        List<String> etapas = debug ? new ArrayList<>() : null;
        
        if (debug) {
            etapas.add("Iniciando geração de jogos para " + tipo.getNome());
        }

        GerarJogoRequest req = normalizarRequest(request, tipo);
        validarRequest(tipo, req);

        if (debug) {
            etapas.add("Configuração: " + req.quantidadeNumeros() + " números por jogo, " + req.quantidadeJogos() + " jogo(s)");
        }

        List<String> estrategias = new ArrayList<>();
        Map<Integer, Double> pesos = calcularPesos(tipo, req, estrategias, debug, etapas);

        Set<Integer> excluidos = req.numerosExcluidos() != null
                ? new HashSet<>(req.numerosExcluidos())
                : Collections.emptySet();

        Set<Integer> obrigatorios = req.numerosObrigatorios() != null
                ? new HashSet<>(req.numerosObrigatorios())
                : Collections.emptySet();

        for (Integer num : excluidos) {
            pesos.remove(num);
        }

        if (debug && !excluidos.isEmpty()) {
            etapas.add("Números excluídos removidos dos pesos: " + excluidos.stream().sorted().toList());
        }

        if (debug && !obrigatorios.isEmpty()) {
            etapas.add("Números obrigatórios incluídos: " + obrigatorios.stream().sorted().toList());
        }

        List<List<Integer>> jogos = new ArrayList<>();
        Set<String> jogosGerados = new HashSet<>();

        int quantidadeJogos = req.quantidadeJogos();
        int tentativas = 0;

        // Super Sete: columns are independent, sequential check doesn't apply
        boolean isColumnBased = tipo == TipoLoteria.SUPER_SETE;
        boolean podeEvitarSequenciais = !isColumnBased
                && Boolean.TRUE.equals(req.evitarSequenciais()) 
                && req.quantidadeNumeros() <= tipo.getNumerosDezenas() / 3;
        
        if (debug && Boolean.TRUE.equals(req.evitarSequenciais()) && !podeEvitarSequenciais) {
            etapas.add("[AVISO] Evitar sequenciais ignorado: impossível com " + req.quantidadeNumeros() + " números de " + tipo.getNumerosDezenas());
        }

        while (jogos.size() < quantidadeJogos && tentativas < MAX_TENTATIVAS) {
            tentativas++;
            List<Integer> jogo = gerarJogo(tipo, req, pesos, obrigatorios, excluidos);

            if (jogo == null) {
                continue;
            }

            if (podeEvitarSequenciais && temSequenciais(jogo)) {
                continue;
            }

            String jogoKey = jogo.toString();
            if (!jogosGerados.contains(jogoKey)) {
                jogosGerados.add(jogoKey);
                jogos.add(jogo);
            }
        }

        if (jogos.isEmpty()) {
            throw new IllegalStateException("Não foi possível gerar jogos com os critérios especificados");
        }

        String estrategia = estrategias.isEmpty() ? "Aleatório" : String.join(" + ", estrategias);

        if (debug) {
            etapas.add("Jogos gerados com sucesso após " + tentativas + " tentativas");
            for (int i = 0; i < jogos.size(); i++) {
                etapas.add("Jogo " + (i + 1) + ": " + jogos.get(i));
            }
        }

        log.info("Gerados {} jogos para {} em {} tentativas", jogos.size(), tipo.getNome(), tentativas);

        String timeSugerido = null;
        String mesSugerido = null;
        List<String> timesSugeridos = null;
        List<String> mesesSugeridos = null;
        List<String> timesTop5 = null;
        List<String> mesesTop5 = null;
        GerarJogoResponse.TimeCoracaoDebug timeCoracaoDebug = null;

        // Sempre gera sugestão de time para Timemania (um por jogo)
        if (tipo == TipoLoteria.TIMEMANIA) {
            try {
                String estrategiaTime = mapearEstrategiaTimeCoracao(req);
                List<TimeCoracaoMesSorteService.SugestaoDetalhada> sugestoes = 
                        timeCoracaoMesSorteService.sugerirMultiplos(tipo, estrategiaTime, jogos.size());
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

        // Sempre gera sugestão de mês para Dia de Sorte (um por jogo)
        if (tipo == TipoLoteria.DIA_DE_SORTE) {
            try {
                String estrategiaMes = mapearEstrategiaMesSorte(req);
                List<TimeCoracaoMesSorteService.SugestaoDetalhada> sugestoes = 
                        timeCoracaoMesSorteService.sugerirMultiplos(tipo, estrategiaMes, jogos.size());
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
            Map<Integer, Long> frequencias = estatisticaService.frequenciaTodosNumeros(tipo);
            Map<Integer, Long> atrasos = estatisticaService.numerosAtrasados(tipo, tipo.getNumerosDezenas());
            
            List<Integer> quentes = frequencias.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList();
            List<Integer> frios = frequencias.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(10).map(Map.Entry::getKey).toList();
            List<Integer> atrasadosList = atrasos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList();

            debugInfo = new GerarJogoResponse.DebugInfo(
                    etapas,
                    new TreeMap<>(pesos),
                    frequencias,
                    atrasos,
                    quentes,
                    frios,
                    atrasadosList,
                    estrategia,
                    timesTop5,
                    mesesTop5,
                    timeCoracaoDebug
            );
        }

        return new GerarJogoResponse(tipo, jogos, estrategia, LocalDateTime.now(), timeSugerido, mesSugerido, timesSugeridos, mesesSugeridos, null, debugInfo);
    }

    private String mapearEstrategiaTimeCoracao(GerarJogoRequest req) {
        // Se usuário especificou estratégia de time, usar ela
        if (req.sugerirTime() != null && !req.sugerirTime().isBlank()) {
            return req.sugerirTime();
        }
        // Mapear baseado nas opções de números selecionadas
        if (Boolean.TRUE.equals(req.usarNumerosQuentes())) return "quente";
        if (Boolean.TRUE.equals(req.usarNumerosFrios())) return "frio";
        if (Boolean.TRUE.equals(req.usarNumerosAtrasados())) return "atrasado";
        return "aleatorio";
    }

    private String mapearEstrategiaMesSorte(GerarJogoRequest req) {
        // Se usuário especificou estratégia de mês, usar ela
        if (req.sugerirMes() != null && !req.sugerirMes().isBlank()) {
            return req.sugerirMes();
        }
        // Mapear baseado nas opções de números selecionadas
        if (Boolean.TRUE.equals(req.usarNumerosQuentes())) return "quente";
        if (Boolean.TRUE.equals(req.usarNumerosFrios())) return "frio";
        if (Boolean.TRUE.equals(req.usarNumerosAtrasados())) return "atrasado";
        return "aleatorio";
    }

    private GerarJogoResponse gerarJogosMaisMilionaria(GerarJogoRequest request, boolean debug) {
        int quantidadeJogos = request != null && request.quantidadeJogos() != null
                ? Math.min(request.quantidadeJogos(), MAX_JOGOS)
                : 1;
        if (quantidadeJogos < 1) quantidadeJogos = 1;

        int qtdDezenas = request != null && request.quantidadeNumeros() != null
                ? Math.max(6, Math.min(request.quantidadeNumeros(), 12))
                : 6;
        int qtdTrevos = request != null && request.quantidadeTrevos() != null
                ? Math.max(2, Math.min(request.quantidadeTrevos(), 6))
                : 2;

        List<String> etapas = debug ? new ArrayList<>() : null;
        List<String> estrategias = new ArrayList<>();
        
        if (debug) {
            etapas.add("Iniciando geração de jogos para +Milionária");
            etapas.add("Configuração: " + qtdDezenas + " dezenas (1-50) + " + qtdTrevos + " trevos (1-6), " + quantidadeJogos + " jogo(s)");
        }

        // Calcular pesos para dezenas (1-50)
        Map<Integer, Double> pesosDezenas = new HashMap<>();
        for (int i = 1; i <= 50; i++) {
            pesosDezenas.put(i, 1.0);
        }

        if (request != null && Boolean.TRUE.equals(request.usarNumerosQuentes())) {
            Map<Integer, Long> frequentes = estatisticaService.numerosMaisFrequentes(TipoLoteria.MAIS_MILIONARIA, 50);
            long maxFreq = frequentes.values().stream().mapToLong(Long::longValue).max().orElse(1);
            for (Map.Entry<Integer, Long> entry : frequentes.entrySet()) {
                double peso = 1.0 + (2.0 * entry.getValue() / maxFreq);
                pesosDezenas.merge(entry.getKey(), peso, (a, b) -> a * b);
            }
            estrategias.add("Números quentes");
            if (debug) etapas.add("Aplicando pesos para números quentes (mais frequentes)");
        }

        if (request != null && Boolean.TRUE.equals(request.usarNumerosFrios())) {
            Map<Integer, Long> menosFrequentes = estatisticaService.numerosMenosFrequentes(TipoLoteria.MAIS_MILIONARIA, 50);
            long minFreq = menosFrequentes.values().stream().mapToLong(Long::longValue).min().orElse(1);
            long maxFreq = menosFrequentes.values().stream().mapToLong(Long::longValue).max().orElse(1);
            long range = maxFreq - minFreq;
            if (range > 0) {
                for (Map.Entry<Integer, Long> entry : menosFrequentes.entrySet()) {
                    double peso = 1.0 + (2.0 * (maxFreq - entry.getValue()) / range);
                    pesosDezenas.merge(entry.getKey(), peso, (a, b) -> a * b);
                }
            }
            estrategias.add("Números frios");
            if (debug) etapas.add("Aplicando pesos para números frios (menos frequentes)");
        }

        if (request != null && Boolean.TRUE.equals(request.usarNumerosAtrasados())) {
            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.MAIS_MILIONARIA, 50);
            long maxAtraso = atrasados.values().stream().mapToLong(Long::longValue).max().orElse(1);
            for (Map.Entry<Integer, Long> entry : atrasados.entrySet()) {
                double peso = 1.0 + (2.0 * entry.getValue() / Math.max(maxAtraso, 1));
                pesosDezenas.merge(entry.getKey(), peso, (a, b) -> a * b);
            }
            estrategias.add("Números atrasados");
            if (debug) etapas.add("Aplicando pesos para números atrasados (há mais tempo sem sair)");
        }

        // Aplicar exclusões e obrigatórios
        Set<Integer> excluidos = request != null && request.numerosExcluidos() != null
                ? new HashSet<>(request.numerosExcluidos())
                : Collections.emptySet();
        Set<Integer> obrigatorios = request != null && request.numerosObrigatorios() != null
                ? new HashSet<>(request.numerosObrigatorios())
                : Collections.emptySet();

        for (Integer num : excluidos) {
            pesosDezenas.remove(num);
        }

        if (debug && !excluidos.isEmpty()) {
            etapas.add("Números excluídos: " + excluidos.stream().sorted().toList());
        }
        if (debug && !obrigatorios.isEmpty()) {
            etapas.add("Números obrigatórios: " + obrigatorios.stream().sorted().toList());
        }

        if (debug) {
            List<Map.Entry<Integer, Double>> topPesos = pesosDezenas.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(10)
                    .toList();
            etapas.add("Top 10 pesos de dezenas: " + topPesos.stream()
                    .map(e -> e.getKey() + "=" + String.format("%.2f", e.getValue()))
                    .collect(Collectors.joining(", ")));
        }

        List<List<Integer>> jogos = new ArrayList<>();
        Set<String> jogosGerados = new HashSet<>();
        int tentativas = 0;

        boolean balancear = request != null && Boolean.TRUE.equals(request.balancearParesImpares());
        if (balancear) {
            estrategias.add("Balanceamento par/ímpar");
            int metade = qtdDezenas / 2;
            if (debug) etapas.add("Balanceamento par/ímpar ativado (" + metade + " pares, " + (qtdDezenas - metade) + " ímpares)");
        }

        while (jogos.size() < quantidadeJogos && tentativas < MAX_TENTATIVAS) {
            tentativas++;

            // Selecionar dezenas usando pesos
            List<Integer> dezenas = selecionarNumerosComPeso(pesosDezenas, qtdDezenas, obrigatorios, balancear);
            if (dezenas == null) continue;
            Collections.sort(dezenas);

            // Selecionar trevos aleatórios (1-6)
            Set<Integer> trevosSet = new HashSet<>();
            while (trevosSet.size() < qtdTrevos) {
                trevosSet.add(random.nextInt(6) + 1);
            }
            List<Integer> trevos = new ArrayList<>(trevosSet);
            Collections.sort(trevos);

            List<Integer> jogo = new ArrayList<>(dezenas);
            jogo.addAll(trevos);

            String jogoKey = jogo.toString();
            if (!jogosGerados.contains(jogoKey)) {
                jogosGerados.add(jogoKey);
                jogos.add(jogo);
            }
        }

        String estrategia = estrategias.isEmpty() ? "Aleatório" : String.join(" + ", estrategias);
        estrategia += " (" + qtdDezenas + " dezenas + " + qtdTrevos + " trevos)";

        if (debug) {
            etapas.add("Jogos gerados com sucesso após " + tentativas + " tentativas");
            for (int i = 0; i < jogos.size(); i++) {
                List<Integer> jogo = jogos.get(i);
                etapas.add("Jogo " + (i + 1) + ": Dezenas " + jogo.subList(0, qtdDezenas) + " + Trevos " + jogo.subList(qtdDezenas, jogo.size()));
            }
        }

        log.info("Gerados {} jogos para +Milionária em {} tentativas ({} dezenas + {} trevos)", jogos.size(), tentativas, qtdDezenas, qtdTrevos);
        
        GerarJogoResponse.DebugInfo debugInfo = null;
        if (debug) {
            Map<Integer, Long> frequencias = estatisticaService.frequenciaTodosNumeros(TipoLoteria.MAIS_MILIONARIA);
            Map<Integer, Long> atrasos = estatisticaService.numerosAtrasados(TipoLoteria.MAIS_MILIONARIA, 50);
            
            List<Integer> quentes = frequencias.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList();
            List<Integer> frios = frequencias.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(10).map(Map.Entry::getKey).toList();
            List<Integer> atrasadosList = atrasos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList();

            debugInfo = new GerarJogoResponse.DebugInfo(
                    etapas,
                    new TreeMap<>(pesosDezenas),
                    frequencias,
                    atrasos,
                    quentes,
                    frios,
                    atrasadosList,
                    estrategia
            );
        }
        
        return new GerarJogoResponse(TipoLoteria.MAIS_MILIONARIA, jogos, estrategia, LocalDateTime.now(),
                null, null, null, null, qtdDezenas, debugInfo);
    }

    private List<Integer> selecionarNumerosComPeso(Map<Integer, Double> pesos, int quantidade, 
                                                    Set<Integer> obrigatorios, boolean balancear) {
        List<Integer> selecionados = new ArrayList<>(obrigatorios);
        Map<Integer, Double> pesosDisponiveis = new HashMap<>(pesos);
        
        for (Integer num : obrigatorios) {
            pesosDisponiveis.remove(num);
        }

        while (selecionados.size() < quantidade && !pesosDisponiveis.isEmpty()) {
            if (balancear) {
                long pares = selecionados.stream().filter(n -> n % 2 == 0).count();
                long impares = selecionados.size() - pares;
                int faltam = quantidade - selecionados.size();
                
                Map<Integer, Double> pesosFiltrados = new HashMap<>();
                if (pares >= 3) {
                    // Só pode selecionar ímpares
                    for (Map.Entry<Integer, Double> e : pesosDisponiveis.entrySet()) {
                        if (e.getKey() % 2 != 0) pesosFiltrados.put(e.getKey(), e.getValue());
                    }
                } else if (impares >= 3) {
                    // Só pode selecionar pares
                    for (Map.Entry<Integer, Double> e : pesosDisponiveis.entrySet()) {
                        if (e.getKey() % 2 == 0) pesosFiltrados.put(e.getKey(), e.getValue());
                    }
                } else {
                    pesosFiltrados = pesosDisponiveis;
                }
                
                if (pesosFiltrados.isEmpty()) {
                    pesosFiltrados = pesosDisponiveis;
                }
                
                Integer num = selecionarPorPeso(pesosFiltrados);
                if (num != null) {
                    selecionados.add(num);
                    pesosDisponiveis.remove(num);
                }
            } else {
                Integer num = selecionarPorPeso(pesosDisponiveis);
                if (num != null) {
                    selecionados.add(num);
                    pesosDisponiveis.remove(num);
                }
            }
        }

        return selecionados.size() == quantidade ? selecionados : null;
    }

    private Integer selecionarPorPeso(Map<Integer, Double> pesos) {
        if (pesos.isEmpty()) return null;
        
        double total = pesos.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = random.nextDouble() * total;
        double acumulado = 0;
        
        for (Map.Entry<Integer, Double> entry : pesos.entrySet()) {
            acumulado += entry.getValue();
            if (r <= acumulado) {
                return entry.getKey();
            }
        }
        
        return pesos.keySet().iterator().next();
    }

    private GerarJogoRequest normalizarRequest(GerarJogoRequest request, TipoLoteria tipo) {
        if (request == null) {
            return new GerarJogoRequest(
                    tipo.getMinimoNumeros(),
                    1,
                    null, null, null, null, null, null, null, null, null, null
            );
        }

        Integer quantidadeNumeros = request.quantidadeNumeros() != null
                ? request.quantidadeNumeros()
                : tipo.getMinimoNumeros();

        Integer quantidadeJogos = request.quantidadeJogos() != null
                ? Math.min(request.quantidadeJogos(), MAX_JOGOS)
                : 1;

        if (quantidadeJogos < 1) {
            quantidadeJogos = 1;
        }

        return new GerarJogoRequest(
                quantidadeNumeros,
                quantidadeJogos,
                request.usarNumerosQuentes(),
                request.usarNumerosFrios(),
                request.usarNumerosAtrasados(),
                request.balancearParesImpares(),
                request.evitarSequenciais(),
                request.numerosObrigatorios(),
                request.numerosExcluidos(),
                request.sugerirTime(),
                request.sugerirMes(),
                request.quantidadeTrevos()
        );
    }

    private void validarRequest(TipoLoteria tipo, GerarJogoRequest request) {
        int qtdNumeros = request.quantidadeNumeros();

        if (qtdNumeros < tipo.getMinimoNumeros() || qtdNumeros > tipo.getMaximoNumeros()) {
            throw new IllegalArgumentException(
                    String.format("Quantidade de números deve estar entre %d e %d para %s",
                            tipo.getMinimoNumeros(), tipo.getMaximoNumeros(), tipo.getNome()));
        }

        if (request.numerosObrigatorios() != null) {
            for (Integer num : request.numerosObrigatorios()) {
                if (num < tipo.getNumeroInicial() || num > tipo.getNumeroFinal()) {
                    throw new IllegalArgumentException(
                            String.format("Número obrigatório %d inválido. Deve estar entre %d e %d",
                                    num, tipo.getNumeroInicial(), tipo.getNumeroFinal()));
                }
            }
            if (request.numerosObrigatorios().size() > qtdNumeros) {
                throw new IllegalArgumentException(
                        "Quantidade de números obrigatórios maior que a quantidade de números no jogo");
            }
        }

        if (request.numerosExcluidos() != null) {
            for (Integer num : request.numerosExcluidos()) {
                if (num < tipo.getNumeroInicial() || num > tipo.getNumeroFinal()) {
                    throw new IllegalArgumentException(
                            String.format("Número excluído %d inválido. Deve estar entre %d e %d",
                                    num, tipo.getNumeroInicial(), tipo.getNumeroFinal()));
                }
            }
        }

        if (request.numerosObrigatorios() != null && request.numerosExcluidos() != null) {
            Set<Integer> obrigatorios = new HashSet<>(request.numerosObrigatorios());
            obrigatorios.retainAll(request.numerosExcluidos());
            if (!obrigatorios.isEmpty()) {
                throw new IllegalArgumentException(
                        "Números não podem estar em obrigatórios e excluídos ao mesmo tempo: " + obrigatorios);
            }
        }

        // Super Sete allows duplicates across columns, so available numbers check doesn't apply
        if (tipo != TipoLoteria.SUPER_SETE) {
            int numerosDisponiveis = tipo.getNumerosDezenas()
                    - (request.numerosExcluidos() != null ? request.numerosExcluidos().size() : 0);
            if (numerosDisponiveis < qtdNumeros) {
                throw new IllegalArgumentException(
                        "Números disponíveis insuficientes para gerar o jogo após exclusões");
            }
        }
    }

    private Map<Integer, Double> calcularPesos(TipoLoteria tipo, GerarJogoRequest request, List<String> estrategias, boolean debug, List<String> etapas) {
        Map<Integer, Double> pesos = new HashMap<>();

        for (int i = tipo.getNumeroInicial(); i <= tipo.getNumeroFinal(); i++) {
            pesos.put(i, 1.0);
        }

        if (Boolean.TRUE.equals(request.usarNumerosQuentes())) {
            Map<Integer, Long> frequentes = estatisticaService.numerosMaisFrequentes(tipo, tipo.getNumerosDezenas());
            long maxFreq = frequentes.values().stream().mapToLong(Long::longValue).max().orElse(1);

            for (Map.Entry<Integer, Long> entry : frequentes.entrySet()) {
                double peso = 1.0 + (2.0 * entry.getValue() / maxFreq);
                pesos.merge(entry.getKey(), peso, (a, b) -> a * b);
            }
            estrategias.add("Números quentes");
            if (debug && etapas != null) {
                etapas.add("Aplicando pesos para números quentes (mais frequentes)");
            }
        }

        if (Boolean.TRUE.equals(request.usarNumerosFrios())) {
            Map<Integer, Long> menosFrequentes = estatisticaService.numerosMenosFrequentes(tipo, tipo.getNumerosDezenas());
            long minFreq = menosFrequentes.values().stream().mapToLong(Long::longValue).min().orElse(1);
            long maxFreq = menosFrequentes.values().stream().mapToLong(Long::longValue).max().orElse(1);
            long range = maxFreq - minFreq;

            if (range > 0) {
                for (Map.Entry<Integer, Long> entry : menosFrequentes.entrySet()) {
                    double peso = 1.0 + (2.0 * (maxFreq - entry.getValue()) / range);
                    pesos.merge(entry.getKey(), peso, (a, b) -> a * b);
                }
            }
            estrategias.add("Números frios");
            if (debug && etapas != null) {
                etapas.add("Aplicando pesos para números frios (menos frequentes)");
            }
        }

        if (Boolean.TRUE.equals(request.usarNumerosAtrasados())) {
            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(tipo, tipo.getNumerosDezenas());
            long maxAtraso = atrasados.values().stream().mapToLong(Long::longValue).max().orElse(1);

            for (Map.Entry<Integer, Long> entry : atrasados.entrySet()) {
                double peso = 1.0 + (2.0 * entry.getValue() / Math.max(maxAtraso, 1));
                pesos.merge(entry.getKey(), peso, (a, b) -> a * b);
            }
            estrategias.add("Números atrasados");
            if (debug && etapas != null) {
                etapas.add("Aplicando pesos para números atrasados (há mais tempo sem sair)");
            }
        }

        if (Boolean.TRUE.equals(request.balancearParesImpares())) {
            estrategias.add("Balanceamento par/ímpar");
            if (debug && etapas != null) {
                etapas.add("Balanceamento par/ímpar ativado");
            }
        }

        if (Boolean.TRUE.equals(request.evitarSequenciais())) {
            estrategias.add("Evitar sequenciais");
            if (debug && etapas != null) {
                etapas.add("Evitar números sequenciais ativado");
            }
        }

        if (request.numerosObrigatorios() != null && !request.numerosObrigatorios().isEmpty()) {
            estrategias.add("Números obrigatórios: " + request.numerosObrigatorios());
        }

        if (request.numerosExcluidos() != null && !request.numerosExcluidos().isEmpty()) {
            estrategias.add("Números excluídos: " + request.numerosExcluidos());
        }

        if (debug && etapas != null) {
            List<Map.Entry<Integer, Double>> topPesos = pesos.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(10)
                    .toList();
            etapas.add("Top 10 números com maior peso: " + topPesos.stream()
                    .map(e -> e.getKey() + "(peso=" + String.format("%.2f", e.getValue()) + ")")
                    .collect(Collectors.joining(", ")));
        }

        return pesos;
    }

    private List<Integer> gerarJogo(TipoLoteria tipo, GerarJogoRequest request,
                                     Map<Integer, Double> pesos, Set<Integer> obrigatorios, Set<Integer> excluidos) {
        int quantidadeNumeros = request.quantidadeNumeros();

        // For Super Sete: generate one digit per column (allows duplicates, preserves order)
        if (tipo == TipoLoteria.SUPER_SETE) {
            List<Integer> jogo = new ArrayList<>(obrigatorios);
            List<Integer> numerosDisponiveis = new ArrayList<>();
            List<Double> pesosDisponiveis = new ArrayList<>();
            for (Map.Entry<Integer, Double> entry : pesos.entrySet()) {
                if (!excluidos.contains(entry.getKey())) {
                    numerosDisponiveis.add(entry.getKey());
                    pesosDisponiveis.add(entry.getValue());
                }
            }
            while (jogo.size() < quantidadeNumeros) {
                Integer numero = selecionarNumeroPonderadoComRepeticao(numerosDisponiveis, pesosDisponiveis);
                if (numero == null) return null;
                jogo.add(numero);
            }
            return jogo; // No sorting! Column order preserved
        }

        Set<Integer> jogo = new HashSet<>(obrigatorios);

        List<Integer> numerosDisponiveis = new ArrayList<>();
        List<Double> pesosDisponiveis = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : pesos.entrySet()) {
            if (!obrigatorios.contains(entry.getKey()) && !excluidos.contains(entry.getKey())) {
                numerosDisponiveis.add(entry.getKey());
                pesosDisponiveis.add(entry.getValue());
            }
        }

        if (numerosDisponiveis.size() + obrigatorios.size() < quantidadeNumeros) {
            return null;
        }

        while (jogo.size() < quantidadeNumeros) {
            Integer numero = selecionarNumeroPonderado(numerosDisponiveis, pesosDisponiveis, jogo, request);

            if (numero == null) {
                return null;
            }

            jogo.add(numero);
        }

        List<Integer> resultado = new ArrayList<>(jogo);
        Collections.sort(resultado);
        return resultado;
    }

    private Integer selecionarNumeroPonderado(List<Integer> numeros, List<Double> pesos,
                                               Set<Integer> jaEscolhidos, GerarJogoRequest request) {
        List<Integer> candidatos = new ArrayList<>();
        List<Double> pesosCandidatos = new ArrayList<>();

        for (int i = 0; i < numeros.size(); i++) {
            Integer num = numeros.get(i);
            if (!jaEscolhidos.contains(num)) {
                if (Boolean.TRUE.equals(request.balancearParesImpares())) {
                    int pares = (int) jaEscolhidos.stream().filter(n -> n % 2 == 0).count();
                    int impares = jaEscolhidos.size() - pares;
                    int totalNumeros = request.quantidadeNumeros();
                    int metade = totalNumeros / 2;

                    boolean isPar = num % 2 == 0;
                    if (isPar && pares >= metade + 1) {
                        continue;
                    }
                    if (!isPar && impares >= metade + 1) {
                        continue;
                    }
                }

                candidatos.add(num);
                pesosCandidatos.add(pesos.get(i));
            }
        }

        if (candidatos.isEmpty()) {
            for (int i = 0; i < numeros.size(); i++) {
                Integer num = numeros.get(i);
                if (!jaEscolhidos.contains(num)) {
                    candidatos.add(num);
                    pesosCandidatos.add(pesos.get(i));
                }
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

    private Integer selecionarNumeroPonderadoComRepeticao(List<Integer> numeros, List<Double> pesos) {
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

    private boolean temSequenciais(List<Integer> jogo) {
        List<Integer> ordenado = new ArrayList<>(jogo);
        Collections.sort(ordenado);

        for (int i = 0; i < ordenado.size() - 1; i++) {
            if (ordenado.get(i + 1) - ordenado.get(i) == 1) {
                return true;
            }
        }
        return false;
    }
}
