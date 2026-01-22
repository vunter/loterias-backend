package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse;
import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse.ItemFrequencia;
import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse.UltimoSorteio;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.entity.TimeTimemania;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.domain.repository.TimeTimemaniaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TimeCoracaoMesSorteService {

    private static final Logger log = LoggerFactory.getLogger(TimeCoracaoMesSorteService.class);

    private final ConcursoRepository concursoRepository;
    private final TimeTimemaniaRepository timeTimemaniaRepository;
    private final java.security.SecureRandom random;
    
    // Cache de times ativos para evitar consultas repetidas (volatile for thread safety)
    private volatile Set<String> timesAtivosNomeCompletoCache = null;
    private volatile Set<String> timesAtivosNomeCache = null;
    private volatile Map<String, String> nomeParaNomeCompletoCache = null;
    private final Object cacheLock = new Object();

    public TimeCoracaoMesSorteService(ConcursoRepository concursoRepository, TimeTimemaniaRepository timeTimemaniaRepository) {
        this.concursoRepository = concursoRepository;
        this.timeTimemaniaRepository = timeTimemaniaRepository;
        this.random = new java.security.SecureRandom();
    }
    
    /**
     * Carrega caches de times ativos (disponíveis no volante online)
     */
    private void carregarCacheTimesAtivos() {
        if (timesAtivosNomeCompletoCache == null) {
            synchronized (cacheLock) {
                if (timesAtivosNomeCompletoCache == null) {
                    List<TimeTimemania> timesAtivos = timeTimemaniaRepository.findByAtivoTrue();
                    
                    Set<String> nomeCompletoSet = timesAtivos.stream()
                            .map(TimeTimemania::getNomeCompleto)
                            .map(String::toUpperCase)
                            .collect(Collectors.toSet());
                    
                    Set<String> nomeSet = timesAtivos.stream()
                            .map(TimeTimemania::getNome)
                            .map(String::toUpperCase)
                            .collect(Collectors.toSet());
                    
                    Map<String, String> nomeMap = new HashMap<>();
                    for (TimeTimemania time : timesAtivos) {
                        String nomeUpper = time.getNome().toUpperCase();
                        if (!nomeMap.containsKey(nomeUpper)) {
                            nomeMap.put(nomeUpper, time.getNomeCompleto());
                        }
                    }
                    
                    // Assign volatile fields last to ensure full initialization
                    timesAtivosNomeCache = nomeSet;
                    nomeParaNomeCompletoCache = nomeMap;
                    timesAtivosNomeCompletoCache = nomeCompletoSet;
                    
                    log.info("Carregados {} times ativos do volante ({} nomes únicos)", 
                            nomeCompletoSet.size(), nomeMap.size());
                }
            }
        }
    }
    
    /**
     * Verifica se um time está disponível no volante online.
     * Aceita tanto o nome completo (FLAMENGO/RJ) quanto o nome simples (FLAMENGO).
     */
    private boolean isTimeAtivo(String nomeTime) {
        if (nomeTime == null || nomeTime.isBlank()) return false;
        
        carregarCacheTimesAtivos();
        String nomeUpper = nomeTime.toUpperCase().trim();
        
        // Verificar primeiro pelo nome completo (ex: FLAMENGO/RJ)
        if (timesAtivosNomeCompletoCache.contains(nomeUpper)) {
            return true;
        }
        
        // Se não encontrou, verificar pelo nome simples (ex: FLAMENGO)
        return timesAtivosNomeCache.contains(nomeUpper);
    }
    
    /**
     * Converte nome de time para formato nome_completo (NOME/UF) se possível.
     * Se o nome já estiver no formato completo, retorna como está.
     * Se o nome simples não for encontrado ou for ambíguo, retorna o original.
     */
    private String converterParaNomeCompleto(String nomeTime) {
        if (nomeTime == null || nomeTime.isBlank()) return nomeTime;
        
        carregarCacheTimesAtivos();
        String nomeUpper = nomeTime.toUpperCase().trim();
        
        // Se já está no formato completo, retornar
        if (timesAtivosNomeCompletoCache.contains(nomeUpper)) {
            return nomeUpper;
        }
        
        // Tentar converter nome simples para completo
        String nomeCompleto = nomeParaNomeCompletoCache.get(nomeUpper);
        if (nomeCompleto != null) {
            return nomeCompleto;
        }
        
        // Retornar original se não conseguiu converter
        return nomeTime;
    }

    @Cacheable(value = CacheConfig.CACHE_TIME_CORACAO, key = "'analise-' + #tipo.name()")
    public TimeCoracaoMesSorteResponse analisarTimeCoracao(TipoLoteria tipo) {
        log.debug("Analisando Time do Coração/Mês da Sorte para {}", tipo.getNome());
        validarTipoLoteria(tipo);

        List<Concurso> concursos = concursoRepository.findByTipoLoteriaWithDezenas(tipo).stream()
                .filter(c -> c.getNomeTimeCoracaoMesSorte() != null && !c.getNomeTimeCoracaoMesSorte().isBlank())
                .sorted(Comparator.comparing(Concurso::getNumero))
                .collect(Collectors.toList());

        if (concursos.isEmpty()) {
            log.warn("Nenhum concurso com Time do Coração/Mês da Sorte encontrado para {}", tipo.getNome());
            return criarRespostaVazia(tipo);
        }

        int totalConcursos = concursos.size();
        Concurso ultimoConcurso = concursos.get(concursos.size() - 1);
        int ultimoNumeroConcurso = ultimoConcurso.getNumero();

        Map<String, Long> frequencias = concursos.stream()
                .collect(Collectors.groupingBy(Concurso::getNomeTimeCoracaoMesSorte, Collectors.counting()));

        Map<String, LocalDate> ultimaAparicao = new HashMap<>();
        Map<String, Integer> ultimoConcursoPorItem = new HashMap<>();

        for (Concurso c : concursos) {
            String item = c.getNomeTimeCoracaoMesSorte();
            ultimaAparicao.put(item, c.getDataApuracao());
            ultimoConcursoPorItem.put(item, c.getNumero());
        }

        List<ItemFrequencia> frequenciaCompleta = frequencias.entrySet().stream()
                .map(entry -> {
                    String nome = entry.getKey();
                    int freq = entry.getValue().intValue();
                    double percentual = Math.round((freq * 100.0 / totalConcursos) * 100.0) / 100.0;
                    int atrasoAtual = ultimoNumeroConcurso - ultimoConcursoPorItem.getOrDefault(nome, 0);
                    LocalDate dataUltimaAparicao = ultimaAparicao.get(nome);
                    return new ItemFrequencia(nome, freq, percentual, atrasoAtual, dataUltimaAparicao);
                })
                .sorted(Comparator.comparingInt(ItemFrequencia::frequencia).reversed())
                .collect(Collectors.toList());

        ItemFrequencia maisFrequente = frequenciaCompleta.isEmpty() ? null : frequenciaCompleta.get(0);
        ItemFrequencia menosFrequente = frequenciaCompleta.isEmpty() ? null : frequenciaCompleta.get(frequenciaCompleta.size() - 1);

        UltimoSorteio ultimoSorteio = new UltimoSorteio(
                ultimoConcurso.getNumero(),
                ultimoConcurso.getDataApuracao(),
                ultimoConcurso.getNomeTimeCoracaoMesSorte()
        );

        String tipoAnalise = tipo == TipoLoteria.TIMEMANIA ? "TIME_CORACAO" : "MES_SORTE";

        log.info("Análise concluída para {} - {} concursos analisados, {} itens distintos",
                tipo.getNome(), totalConcursos, frequencias.size());

        return new TimeCoracaoMesSorteResponse(
                tipo,
                tipo.getNome(),
                tipoAnalise,
                totalConcursos,
                maisFrequente,
                menosFrequente,
                frequenciaCompleta,
                ultimoSorteio
        );
    }

    public Map<String, Object> sugerirTimeOuMes(TipoLoteria tipo, String estrategia) {
        log.debug("Sugerindo Time/Mês para {} com estratégia {}", tipo.getNome(), estrategia);
        validarTipoLoteria(tipo);

        TimeCoracaoMesSorteResponse analise = analisarTimeCoracao(tipo);

        if (analise.frequenciaCompleta().isEmpty()) {
            return Map.of(
                    "sugestao", "",
                    "estrategia", estrategia,
                    "motivo", "Nenhum dado disponível para análise"
            );
        }

        ItemFrequencia sugerido;
        String motivo;

        switch (estrategia.toLowerCase()) {
            case "quente" -> {
                sugerido = analise.maisFrequente();
                motivo = String.format("Mais frequente com %d aparições (%.2f%%)",
                        sugerido.frequencia(), sugerido.percentual());
            }
            case "frio" -> {
                sugerido = analise.menosFrequente();
                motivo = String.format("Menos frequente com %d aparições (%.2f%%)",
                        sugerido.frequencia(), sugerido.percentual());
            }
            case "atrasado" -> {
                sugerido = analise.frequenciaCompleta().stream()
                        .max(Comparator.comparingInt(ItemFrequencia::atrasoAtual))
                        .orElse(analise.maisFrequente());
                motivo = String.format("Maior atraso com %d concursos sem aparecer", sugerido.atrasoAtual());
            }
            case "aleatorio" -> {
                List<ItemFrequencia> lista = analise.frequenciaCompleta();
                sugerido = lista.get(random.nextInt(lista.size()));
                motivo = "Selecionado aleatoriamente";
            }
            default -> throw new IllegalArgumentException(
                    "Estratégia inválida: " + estrategia + ". Use: quente, frio, atrasado ou aleatorio");
        }

        log.info("Sugestão gerada para {}: {} ({}) - {}", tipo.getNome(), sugerido.nome(), estrategia, motivo);

        return Map.of(
                "sugestao", sugerido.nome(),
                "estrategia", estrategia,
                "motivo", motivo,
                "frequencia", sugerido.frequencia(),
                "percentual", sugerido.percentual(),
                "atrasoAtual", sugerido.atrasoAtual()
        );
    }

    private void validarTipoLoteria(TipoLoteria tipo) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            throw new IllegalArgumentException(
                    "Análise de Time do Coração/Mês da Sorte disponível apenas para TIMEMANIA e DIA_DE_SORTE. Tipo informado: " + tipo);
        }
    }

    private TimeCoracaoMesSorteResponse criarRespostaVazia(TipoLoteria tipo) {
        String tipoAnalise = tipo == TipoLoteria.TIMEMANIA ? "TIME_CORACAO" : "MES_SORTE";
        return new TimeCoracaoMesSorteResponse(
                tipo,
                tipo.getNome(),
                tipoAnalise,
                0,
                null,
                null,
                List.of(),
                null
        );
    }

    public record SugestaoDetalhada(
        String sugestao,
        String estrategia,
        String motivo,
        int frequencia,
        double percentual,
        int atrasoAtual,
        List<ItemInfo> ranking
    ) {
        public record ItemInfo(String nome, int frequencia, double percentual, int atraso) {}
    }

    public SugestaoDetalhada sugerirComDetalhes(TipoLoteria tipo, String estrategia) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            throw new IllegalArgumentException("Sugestão de time/mês só disponível para Timemania e Dia de Sorte");
        }

        TimeCoracaoMesSorteResponse analise = analisarTimeCoracao(tipo);

        if (analise.frequenciaCompleta().isEmpty()) {
            return new SugestaoDetalhada("", estrategia, "Nenhum dado disponível", 0, 0, 0, List.of());
        }

        // Para Timemania, filtrar apenas times ativos (disponíveis no volante online)
        List<ItemFrequencia> frequenciasFiltradas;
        if (tipo == TipoLoteria.TIMEMANIA) {
            frequenciasFiltradas = analise.frequenciaCompleta().stream()
                    .filter(item -> isTimeAtivo(item.nome()))
                    .toList();
            log.debug("Filtrados {} times ativos de {} totais", frequenciasFiltradas.size(), analise.frequenciaCompleta().size());
        } else {
            frequenciasFiltradas = analise.frequenciaCompleta();
        }

        if (frequenciasFiltradas.isEmpty()) {
            log.warn("Nenhum time ativo encontrado para sugestão");
            return new SugestaoDetalhada("", estrategia, "Nenhum time ativo disponível", 0, 0, 0, List.of());
        }

        String estrategiaLower = estrategia != null ? estrategia.toLowerCase().trim() : "aleatorio";

        ItemFrequencia sugerido;
        String motivo;
        List<SugestaoDetalhada.ItemInfo> ranking;

        switch (estrategiaLower) {
            case "quente" -> {
                List<ItemFrequencia> ordenados = frequenciasFiltradas.stream()
                        .sorted(Comparator.comparingInt(ItemFrequencia::frequencia).reversed())
                        .toList();
                sugerido = ordenados.get(random.nextInt(Math.min(3, ordenados.size())));
                motivo = String.format("Selecionado entre os 3 mais frequentes (%d aparições, %.2f%%)",
                        sugerido.frequencia(), sugerido.percentual());
                ranking = ordenados.stream().limit(10)
                        .map(i -> new SugestaoDetalhada.ItemInfo(i.nome(), i.frequencia(), i.percentual(), i.atrasoAtual()))
                        .toList();
            }
            case "frio" -> {
                List<ItemFrequencia> ordenados = frequenciasFiltradas.stream()
                        .sorted(Comparator.comparingInt(ItemFrequencia::frequencia))
                        .toList();
                sugerido = ordenados.get(random.nextInt(Math.min(3, ordenados.size())));
                motivo = String.format("Selecionado entre os 3 menos frequentes (%d aparições, %.2f%%)",
                        sugerido.frequencia(), sugerido.percentual());
                ranking = ordenados.stream().limit(10)
                        .map(i -> new SugestaoDetalhada.ItemInfo(i.nome(), i.frequencia(), i.percentual(), i.atrasoAtual()))
                        .toList();
            }
            case "atrasado" -> {
                List<ItemFrequencia> ordenados = frequenciasFiltradas.stream()
                        .sorted(Comparator.comparingInt(ItemFrequencia::atrasoAtual).reversed())
                        .toList();
                sugerido = ordenados.get(random.nextInt(Math.min(3, ordenados.size())));
                motivo = String.format("Selecionado entre os 3 mais atrasados (%d concursos sem aparecer)",
                        sugerido.atrasoAtual());
                ranking = ordenados.stream().limit(10)
                        .map(i -> new SugestaoDetalhada.ItemInfo(i.nome(), i.frequencia(), i.percentual(), i.atrasoAtual()))
                        .toList();
            }
            default -> {
                sugerido = frequenciasFiltradas.get(random.nextInt(frequenciasFiltradas.size()));
                motivo = "Selecionado aleatoriamente entre times ativos";
                ranking = frequenciasFiltradas.stream()
                        .sorted(Comparator.comparingInt(ItemFrequencia::frequencia).reversed())
                        .limit(10)
                        .map(i -> new SugestaoDetalhada.ItemInfo(i.nome(), i.frequencia(), i.percentual(), i.atrasoAtual()))
                        .toList();
            }
        }

        // Para Timemania, converter nome simples para formato NOME/UF
        String nomeParaSugestao = sugerido.nome();
        if (tipo == TipoLoteria.TIMEMANIA) {
            nomeParaSugestao = converterParaNomeCompleto(sugerido.nome());
        }

        log.info("Sugestão detalhada para {}: {} ({}) - {}", tipo.getNome(), nomeParaSugestao, estrategiaLower, motivo);

        return new SugestaoDetalhada(
                nomeParaSugestao,
                estrategiaLower,
                motivo,
                sugerido.frequencia(),
                sugerido.percentual(),
                sugerido.atrasoAtual(),
                ranking
        );
    }

    /**
     * Sugere múltiplos times/meses distintos para jogos múltiplos.
     * Evita repetição a menos que a estatística suporte (ex: se há poucos itens disponíveis).
     * Para Timemania, filtra apenas times ativos (disponíveis no volante online).
     */
    public List<SugestaoDetalhada> sugerirMultiplos(TipoLoteria tipo, String estrategia, int quantidade) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            throw new IllegalArgumentException("Sugestão de time/mês só disponível para Timemania e Dia de Sorte");
        }

        TimeCoracaoMesSorteResponse analise = analisarTimeCoracao(tipo);
        if (analise.frequenciaCompleta().isEmpty()) {
            return Collections.nCopies(quantidade, new SugestaoDetalhada("", estrategia, "Nenhum dado disponível", 0, 0, 0, List.of()));
        }

        // Para Timemania, filtrar apenas times ativos (disponíveis no volante online)
        List<ItemFrequencia> frequenciasFiltradas;
        if (tipo == TipoLoteria.TIMEMANIA) {
            frequenciasFiltradas = analise.frequenciaCompleta().stream()
                    .filter(item -> isTimeAtivo(item.nome()))
                    .toList();
            log.debug("sugerirMultiplos: Filtrados {} times ativos de {} totais", frequenciasFiltradas.size(), analise.frequenciaCompleta().size());
        } else {
            frequenciasFiltradas = analise.frequenciaCompleta();
        }

        if (frequenciasFiltradas.isEmpty()) {
            log.warn("Nenhum time ativo encontrado para sugestão múltipla");
            return Collections.nCopies(quantidade, new SugestaoDetalhada("", estrategia, "Nenhum time ativo disponível", 0, 0, 0, List.of()));
        }

        String estrategiaLower = estrategia != null ? estrategia.toLowerCase().trim() : "aleatorio";
        List<ItemFrequencia> ordenados = ordenarPorEstrategia(frequenciasFiltradas, estrategiaLower);
        
        List<SugestaoDetalhada> sugestoes = new ArrayList<>();
        Set<String> jaUsados = new HashSet<>();
        
        // Ranking geral para debug
        List<SugestaoDetalhada.ItemInfo> ranking = ordenados.stream()
                .limit(10)
                .map(i -> new SugestaoDetalhada.ItemInfo(i.nome(), i.frequencia(), i.percentual(), i.atrasoAtual()))
                .toList();

        for (int i = 0; i < quantidade; i++) {
            ItemFrequencia sugerido = null;
            String motivo;

            // Primeiro, tentar encontrar um item ainda não usado
            List<ItemFrequencia> disponiveis = ordenados.stream()
                    .filter(item -> !jaUsados.contains(item.nome()))
                    .toList();

            if (disponiveis.isEmpty()) {
                // Se todos já foram usados, permitir repetição baseado em estatística
                // Resetar e usar os melhores novamente
                jaUsados.clear();
                disponiveis = ordenados;
                log.debug("Todos os times/meses já foram sugeridos, permitindo repetição para {}", tipo.getNome());
            }

            // Selecionar baseado na estratégia
            int poolSize = Math.min(3, disponiveis.size());
            int selectedIndex = random.nextInt(poolSize);
            sugerido = disponiveis.get(selectedIndex);
            jaUsados.add(sugerido.nome());

            motivo = criarMotivoParaEstrategia(estrategiaLower, sugerido, i + 1, quantidade);

            // Para Timemania, converter nome simples para formato NOME/UF
            String nomeParaSugestao = sugerido.nome();
            if (tipo == TipoLoteria.TIMEMANIA) {
                nomeParaSugestao = converterParaNomeCompleto(sugerido.nome());
            }

            sugestoes.add(new SugestaoDetalhada(
                    nomeParaSugestao,
                    estrategiaLower,
                    motivo,
                    sugerido.frequencia(),
                    sugerido.percentual(),
                    sugerido.atrasoAtual(),
                    ranking
            ));
        }

        log.info("Geradas {} sugestões de {} para {} com estratégia {}", 
                quantidade, tipo == TipoLoteria.TIMEMANIA ? "times" : "meses", tipo.getNome(), estrategiaLower);

        return sugestoes;
    }

    private List<ItemFrequencia> ordenarPorEstrategia(List<ItemFrequencia> itens, String estrategia) {
        return switch (estrategia) {
            case "quente" -> itens.stream()
                    .sorted(Comparator.comparingInt(ItemFrequencia::frequencia).reversed())
                    .toList();
            case "frio" -> itens.stream()
                    .sorted(Comparator.comparingInt(ItemFrequencia::frequencia))
                    .toList();
            case "atrasado" -> itens.stream()
                    .sorted(Comparator.comparingInt(ItemFrequencia::atrasoAtual).reversed())
                    .toList();
            default -> {
                List<ItemFrequencia> shuffled = new ArrayList<>(itens);
                Collections.shuffle(shuffled, random);
                yield shuffled;
            }
        };
    }

    private String criarMotivoParaEstrategia(String estrategia, ItemFrequencia item, int jogoAtual, int totalJogos) {
        String prefixo = totalJogos > 1 ? String.format("Jogo %d/%d: ", jogoAtual, totalJogos) : "";
        return switch (estrategia) {
            case "quente" -> prefixo + String.format("Selecionado entre os mais frequentes (%d aparições, %.2f%%)",
                    item.frequencia(), item.percentual());
            case "frio" -> prefixo + String.format("Selecionado entre os menos frequentes (%d aparições, %.2f%%)",
                    item.frequencia(), item.percentual());
            case "atrasado" -> prefixo + String.format("Selecionado entre os mais atrasados (%d concursos sem aparecer)",
                    item.atrasoAtual());
            default -> prefixo + "Selecionado aleatoriamente";
        };
    }

    public String sugerirTimeOuMesLegado(TipoLoteria tipo, String estrategia) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            throw new IllegalArgumentException("Sugestão de time/mês só disponível para Timemania e Dia de Sorte");
        }

        if (estrategia == null || estrategia.isBlank()) {
            estrategia = "aleatorio";
        }

        String estrategiaLower = estrategia.toLowerCase().trim();

        return switch (estrategiaLower) {
            case "quente" -> sugerirQuente(tipo);
            case "frio" -> sugerirFrio(tipo);
            case "atrasado" -> sugerirAtrasado(tipo);
            case "aleatorio" -> sugerirAleatorioLegado(tipo);
            default -> sugerirAleatorioLegado(tipo);
        };
    }

    public List<String> getTop5(TipoLoteria tipo, String estrategia) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            return Collections.emptyList();
        }

        if (estrategia == null || estrategia.isBlank()) {
            return Collections.emptyList();
        }

        String estrategiaLower = estrategia.toLowerCase().trim();

        return switch (estrategiaLower) {
            case "quente" -> getTopQuentes(tipo, 5);
            case "frio" -> getTopFrios(tipo, 5);
            case "atrasado" -> getTopAtrasados(tipo, 5);
            case "aleatorio" -> getAleatorios(tipo, 5);
            default -> Collections.emptyList();
        };
    }

    private String sugerirQuente(TipoLoteria tipo) {
        List<String> quentes = getTopQuentes(tipo, 5);
        if (quentes.isEmpty()) {
            log.warn("Nenhum time/mês encontrado para {} (quente)", tipo.getNome());
            return null;
        }
        return quentes.get(random.nextInt(Math.min(3, quentes.size())));
    }

    private String sugerirFrio(TipoLoteria tipo) {
        List<String> frios = getTopFrios(tipo, 5);
        if (frios.isEmpty()) {
            log.warn("Nenhum time/mês encontrado para {} (frio)", tipo.getNome());
            return null;
        }
        return frios.get(random.nextInt(Math.min(3, frios.size())));
    }

    private String sugerirAtrasado(TipoLoteria tipo) {
        List<String> atrasados = getTopAtrasados(tipo, 5);
        if (atrasados.isEmpty()) {
            log.warn("Nenhum time/mês encontrado para {} (atrasado)", tipo.getNome());
            return null;
        }
        return atrasados.get(random.nextInt(Math.min(3, atrasados.size())));
    }

    private String sugerirAleatorioLegado(TipoLoteria tipo) {
        List<String> todos = getAleatorios(tipo, 100);
        if (todos.isEmpty()) {
            log.warn("Nenhum time/mês encontrado para {} (aleatorio)", tipo.getNome());
            return null;
        }
        return todos.get(random.nextInt(todos.size()));
    }

    private List<String> getTopQuentes(TipoLoteria tipo, int quantidade) {
        List<Object[]> frequencias = concursoRepository.findFrequenciaTimeCoracao(tipo.name());
        return frequencias.stream()
                .map(row -> (String) row[0])
                .filter(nome -> tipo != TipoLoteria.TIMEMANIA || isTimeAtivo(nome))
                .map(nome -> tipo == TipoLoteria.TIMEMANIA ? converterParaNomeCompleto(nome) : nome)
                .limit(quantidade)
                .collect(Collectors.toList());
    }

    private List<String> getTopFrios(TipoLoteria tipo, int quantidade) {
        List<Object[]> frequencias = concursoRepository.findFrequenciaTimeCoracao(tipo.name());
        List<Object[]> reversed = new ArrayList<>(frequencias);
        Collections.reverse(reversed);
        return reversed.stream()
                .map(row -> (String) row[0])
                .filter(nome -> tipo != TipoLoteria.TIMEMANIA || isTimeAtivo(nome))
                .map(nome -> tipo == TipoLoteria.TIMEMANIA ? converterParaNomeCompleto(nome) : nome)
                .limit(quantidade)
                .collect(Collectors.toList());
    }

    private List<String> getTopAtrasados(TipoLoteria tipo, int quantidade) {
        List<Object[]> ultimaAparicao = concursoRepository.findUltimaAparicaoTimeCoracao(tipo.name());
        return ultimaAparicao.stream()
                .sorted((a, b) -> {
                    Number numA = (Number) a[1];
                    Number numB = (Number) b[1];
                    return Long.compare(numA.longValue(), numB.longValue());
                })
                .map(row -> (String) row[0])
                .filter(nome -> tipo != TipoLoteria.TIMEMANIA || isTimeAtivo(nome))
                .map(nome -> tipo == TipoLoteria.TIMEMANIA ? converterParaNomeCompleto(nome) : nome)
                .limit(quantidade)
                .collect(Collectors.toList());
    }

    private List<String> getAleatorios(TipoLoteria tipo, int quantidade) {
        List<Object[]> frequencias = concursoRepository.findFrequenciaTimeCoracao(tipo.name());
        List<String> todos = frequencias.stream()
                .map(row -> (String) row[0])
                .filter(nome -> tipo != TipoLoteria.TIMEMANIA || isTimeAtivo(nome))
                .map(nome -> tipo == TipoLoteria.TIMEMANIA ? converterParaNomeCompleto(nome) : nome)
                .collect(Collectors.toList());
        Collections.shuffle(todos, random);
        return todos.stream().limit(quantidade).collect(Collectors.toList());
    }

    /**
     * Retorna a lista de todos os times ativos disponíveis no volante da Timemania
     */
    public List<TimeTimemania> listarTimesAtivos() {
        return timeTimemaniaRepository.findByAtivoTrue();
    }

    /**
     * Invalida o cache de times ativos (para ser chamado após atualização)
     */
    public void invalidarCacheTimesAtivos() {
        synchronized (cacheLock) {
            timesAtivosNomeCompletoCache = null;
            timesAtivosNomeCache = null;
            nomeParaNomeCompletoCache = null;
        }
        log.info("Cache de times ativos invalidado");
    }
}
