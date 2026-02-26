package br.com.loterias.service;

import br.com.loterias.domain.dto.AcumuladoResponse;
import br.com.loterias.domain.dto.DashboardResponse;
import br.com.loterias.domain.dto.DashboardResponse.*;
import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.dto.GanhadoresUFResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ConcursoRepository concursoRepository;
    private final CaixaApiClient caixaApiClient;
    private final ConcursoMapper concursoMapper;

    public DashboardService(ConcursoRepository concursoRepository, 
                           CaixaApiClient caixaApiClient,
                           ConcursoMapper concursoMapper) {
        this.concursoRepository = concursoRepository;
        this.caixaApiClient = caixaApiClient;
        this.concursoMapper = concursoMapper;
    }

    /**
     * Generates the dashboard. First runs the transactional DB queries, then
     * fetches external API data outside the transaction boundary to avoid
     * holding a DB connection during external HTTP calls.
     */
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD, key = "#tipo.name()")
    public DashboardResponse gerarDashboard(TipoLoteria tipo) {
        log.info("Gerando dashboard para {}", tipo.getNome());

        DashboardResponse response = gerarDashboardFromDb(tipo);

        // If the last winner info has no ganhadores but should, fetch from external API
        // This happens OUTSIDE the @Transactional to not hold a DB connection
        if (response.ultimoConcursoComGanhador() != null
                && response.ultimoConcursoComGanhador().ganhadores().isEmpty()
                && response.ultimoConcursoComGanhador().totalGanhadores() > 0) {
            List<GanhadorInfo> ganhadores = buscarGanhadoresNaApi(tipo, response.ultimoConcursoComGanhador().numero());
            if (!ganhadores.isEmpty()) {
                var original = response.ultimoConcursoComGanhador();
                var updated = new UltimoConcursoComGanhadorInfo(
                        original.numero(), original.data(), original.dezenas(),
                        original.dezenasSegundoSorteio(),
                        original.totalGanhadores(), original.premioPorGanhador(),
                        original.premioTotal(), ganhadores, original.concursosDesdeUltimoGanhador()
                );
                response = new DashboardResponse(
                        response.tipo(), response.nomeLoteria(), response.resumo(),
                        response.ultimoConcurso(), updated,
                        response.numerosQuentes(), response.numerosFrios(),
                        response.numerosAtrasados(), response.padroes(),
                        response.proximoConcurso(), response.timeCoracaoInfo()
                );
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public DashboardResponse gerarDashboardFromDb(TipoLoteria tipo) {

        Optional<Concurso> ultimoOpt = concursoRepository.findTopByTipoLoteriaOrderByNumeroDescSimple(tipo.name());
        if (ultimoOpt.isEmpty()) {
            return criarDashboardVazio(tipo);
        }

        Concurso ultimoBase = ultimoOpt.get();
        Optional<Concurso> ultimoCompletoOpt = concursoRepository.findByTipoLoteriaAndNumero(tipo, ultimoBase.getNumero());
        Concurso ultimo = ultimoCompletoOpt.orElse(ultimoBase);
        long totalConcursos = concursoRepository.countByTipoLoteria(tipo);

        ResumoGeral resumo = calcularResumoGeralRapido(tipo, totalConcursos, ultimo);
        UltimoConcursoInfo ultimoConcursoInfo = extrairInfoUltimoConcurso(ultimo);
        UltimoConcursoComGanhadorInfo ultimoComGanhador = buscarUltimoConcursoComGanhador(tipo, ultimo.getNumero());
        List<Object[]> frequenciaDezenas = concursoRepository.findFrequenciaDezenas(tipo.name());
        List<Integer> quentes = calcularNumerosQuentes(frequenciaDezenas, 10);
        List<Integer> frios = calcularNumerosFrios(frequenciaDezenas, 10);
        List<Integer> atrasados = calcularNumerosAtrasados(tipo, 10);
        AnalisePatterns padroes = analisarPadroesRapido(tipo, frequenciaDezenas);
        ProximoConcursoInfo proximo = estimarProximoConcurso(ultimo, tipo);

        TimeCoracaoInfo timeCoracaoInfo = null;
        if (tipo == TipoLoteria.TIMEMANIA || tipo == TipoLoteria.DIA_DE_SORTE) {
            timeCoracaoInfo = calcularTimeCoracaoInfo(tipo, ultimo);
        }

        return new DashboardResponse(
                tipo, tipo.getNome(), resumo, ultimoConcursoInfo, ultimoComGanhador,
                quentes, frios, atrasados, padroes, proximo, timeCoracaoInfo
        );
    }

    private TimeCoracaoInfo calcularTimeCoracaoInfo(TipoLoteria tipo, Concurso ultimo) {
        String tipoInfo = tipo == TipoLoteria.TIMEMANIA ? "TIME_CORACAO" : "MES_SORTE";
        String valorAtual = ultimo.getNomeTimeCoracaoMesSorte();

        List<Object[]> frequencias = concursoRepository.findFrequenciaTimeCoracao(tipo.name());
        if (frequencias.isEmpty()) {
            return null;
        }

        String maisFrequente = (String) frequencias.get(0)[0];
        String menosFrequente = (String) frequencias.get(frequencias.size() - 1)[0];

        List<String> top5 = frequencias.stream()
                .limit(5)
                .map(row -> (String) row[0])
                .collect(Collectors.toList());

        Optional<Integer> maxOpt = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        int ultimoConcurso = maxOpt.orElse(0);

        List<Object[]> ultimaAparicao = concursoRepository.findUltimaAparicaoTimeCoracao(tipo.name());
        
        String maisAtrasado = null;
        int atrasoMaisAtrasado = 0;
        
        for (Object[] row : ultimaAparicao) {
            String nome = (String) row[0];
            int ultimaAp = ((Number) row[1]).intValue();
            int atraso = ultimoConcurso - ultimaAp;
            if (atraso > atrasoMaisAtrasado) {
                atrasoMaisAtrasado = atraso;
                maisAtrasado = nome;
            }
        }

        return new TimeCoracaoInfo(tipoInfo, valorAtual, maisFrequente, menosFrequente, maisAtrasado, atrasoMaisAtrasado, top5);
    }

    private ResumoGeral calcularResumoGeralRapido(TipoLoteria tipo, long totalConcursos, Concurso ultimo) {
        LocalDate ultimoData = ultimo.getDataApuracao();
        int diasSemSorteio = ultimoData != null ? Math.max(0, (int) ChronoUnit.DAYS.between(ultimoData, LocalDate.now())) : 0;

        BigDecimal premioUltimo = BigDecimal.ZERO;
        Optional<FaixaPremiacao> faixa1 = ultimo.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst();
        if (faixa1.isPresent() && faixa1.get().getValorPremio() != null) {
            premioUltimo = faixa1.get().getValorPremio();
        }

        return new ResumoGeral(
                (int) totalConcursos, null, ultimoData, diasSemSorteio,
                premioUltimo, ultimo.getNumero(), 0.0
        );
    }

    private UltimoConcursoInfo extrairInfoUltimoConcurso(Concurso ultimo) {
        BigDecimal valorAcumulado = ultimo.getValorAcumuladoProximoConcurso();
        boolean acumulou = ultimo.getAcumulado() != null && ultimo.getAcumulado();

        int ganhadoresCount = 0;
        BigDecimal premio = BigDecimal.ZERO;
        Optional<FaixaPremiacao> faixa1 = ultimo.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst();
        if (faixa1.isPresent()) {
            ganhadoresCount = faixa1.get().getNumeroGanhadores() != null ? faixa1.get().getNumeroGanhadores() : 0;
            premio = faixa1.get().getValorPremio() != null ? faixa1.get().getValorPremio() : BigDecimal.ZERO;
        }

        // Mapear ganhadores com detalhes
        List<GanhadorInfo> ganhadores = ultimo.getGanhadoresUF().stream()
                .map(g -> new GanhadorInfo(
                        g.getUf(),
                        g.getCidade(),
                        g.getNumeroGanhadores() != null ? g.getNumeroGanhadores() : 0,
                        g.getCanal()
                ))
                .collect(Collectors.toList());

        // Use draw order when available; include second draw for Dupla Sena
        List<Integer> dezenas = extrairDezenasExibicao(ultimo);
        List<Integer> dezenasSegundo = extrairDezenasSegundoSorteio(ultimo);

        return new UltimoConcursoInfo(
                ultimo.getNumero(), ultimo.getDataApuracao(),
                dezenas, dezenasSegundo, acumulou, valorAcumulado,
                ganhadoresCount, premio, ganhadores
        );
    }

    private UltimoConcursoComGanhadorInfo buscarUltimoConcursoComGanhador(TipoLoteria tipo, int ultimoNumero) {
        // Busca o último concurso que teve ganhador na faixa principal
        Optional<Concurso> concursoBaseOpt = concursoRepository
                .findUltimoConcursoComGanhador(tipo.name());
        
        if (concursoBaseOpt.isEmpty()) {
            return null;
        }
        
        // Carrega o concurso completo com todas as coleções
        Optional<Concurso> concursoCompletoOpt = concursoRepository
                .findByTipoLoteriaAndNumero(tipo, concursoBaseOpt.get().getNumero());
        
        if (concursoCompletoOpt.isEmpty()) {
            return null;
        }
        
        Concurso concurso = concursoCompletoOpt.get();
        
        int totalGanhadores = 0;
        BigDecimal premioPorGanhador = BigDecimal.ZERO;
        
        Optional<FaixaPremiacao> faixa1 = concurso.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst();
        
        if (faixa1.isPresent()) {
            totalGanhadores = faixa1.get().getNumeroGanhadores() != null ? faixa1.get().getNumeroGanhadores() : 0;
            premioPorGanhador = faixa1.get().getValorPremio() != null ? faixa1.get().getValorPremio() : BigDecimal.ZERO;
        }
        
        BigDecimal premioTotal = premioPorGanhador.multiply(BigDecimal.valueOf(totalGanhadores));
        
        // If no UF data, return empty list — will be resolved outside transaction
        List<GanhadorInfo> ganhadores;
        if (concurso.getGanhadoresUF().isEmpty()) {
            ganhadores = List.of();
        } else {
            ganhadores = concurso.getGanhadoresUF().stream()
                    .map(g -> new GanhadorInfo(
                            g.getUf(),
                            g.getCidade(),
                            g.getNumeroGanhadores() != null ? g.getNumeroGanhadores() : 0,
                            g.getCanal()
                    ))
                    .collect(Collectors.toList());
        }
        
        int concursosDesde = ultimoNumero - concurso.getNumero();
        
        return new UltimoConcursoComGanhadorInfo(
                concurso.getNumero(),
                concurso.getDataApuracao(),
                extrairDezenasExibicao(concurso),
                extrairDezenasSegundoSorteio(concurso),
                totalGanhadores,
                premioPorGanhador,
                premioTotal,
                ganhadores,
                concursosDesde
        );
    }

    /**
     * Returns dezenas in draw order for lotteries where position matters
     * (Super Sete columns, Dupla Sena draw sequence).
     * Falls back to the sorted list when draw-order data is unavailable.
     */
    private List<Integer> extrairDezenasExibicao(Concurso concurso) {
        TipoLoteria tipo = concurso.getTipoLoteria();
        List<Integer> ordemSorteio = concurso.getDezenasSorteadasOrdemSorteio();

        if (tipo == TipoLoteria.SUPER_SETE || tipo == TipoLoteria.DUPLA_SENA) {
            if (ordemSorteio != null && !ordemSorteio.isEmpty()) {
                if (tipo == TipoLoteria.DUPLA_SENA) {
                    // First half = 1st draw in draw order
                    int half = concurso.getDezenasSorteadas().size();
                    return ordemSorteio.subList(0, Math.min(half, ordemSorteio.size()));
                }
                return ordemSorteio;
            }
        }
        return concurso.getDezenasSorteadas();
    }

    /**
     * Returns the second draw dezenas for Dupla Sena in draw order.
     * Returns null for all other lotteries.
     */
    private List<Integer> extrairDezenasSegundoSorteio(Concurso concurso) {
        if (concurso.getTipoLoteria() != TipoLoteria.DUPLA_SENA) {
            return null;
        }
        List<Integer> ordemSorteio = concurso.getDezenasSorteadasOrdemSorteio();
        int half = concurso.getDezenasSorteadas().size();
        if (ordemSorteio != null && ordemSorteio.size() > half) {
            // Second half = 2nd draw in draw order
            return ordemSorteio.subList(half, ordemSorteio.size());
        }
        // Fallback to stored second draw (sorted)
        List<Integer> segundo = concurso.getDezenasSegundoSorteio();
        return (segundo != null && !segundo.isEmpty()) ? segundo : null;
    }
    
    private List<GanhadorInfo> buscarGanhadoresNaApi(TipoLoteria tipo, int numeroConcurso) {
        log.info("Buscando dados de ganhadores na API da Caixa para {} concurso {}", tipo.getNome(), numeroConcurso);
        
        try {
            Optional<CaixaApiResponse> responseOpt = caixaApiClient.buscarConcurso(tipo, numeroConcurso);
            
            if (responseOpt.isPresent()) {
                CaixaApiResponse response = responseOpt.get();
                
                if (response.listaMunicipioUFGanhadores() != null && !response.listaMunicipioUFGanhadores().isEmpty()) {
                    log.info("Concurso {} tem {} ganhadores UF na API", 
                            numeroConcurso, response.listaMunicipioUFGanhadores().size());
                    
                    return response.listaMunicipioUFGanhadores().stream()
                            .map(g -> new GanhadorInfo(
                                    g.uf(),
                                    g.municipio(),
                                    g.ganhadores() != null ? g.ganhadores() : 0,
                                    g.canal()
                            ))
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar dados de ganhadores na API: {}", e.getMessage());
        }
        
        return List.of();
    }

    private List<Integer> calcularNumerosQuentes(List<Object[]> freq, int quantidade) {
        return freq.stream()
                .sorted((a, b) -> Long.compare(((Number) b[1]).longValue(), ((Number) a[1]).longValue()))
                .limit(quantidade)
                .map(row -> ((Number) row[0]).intValue())
                .collect(Collectors.toList());
    }

    private List<Integer> calcularNumerosFrios(List<Object[]> freq, int quantidade) {
        return freq.stream()
                .sorted(Comparator.comparingLong(a -> ((Number) a[1]).longValue()))
                .limit(quantidade)
                .map(row -> ((Number) row[0]).intValue())
                .collect(Collectors.toList());
    }

    private List<Integer> calcularNumerosAtrasados(TipoLoteria tipo, int quantidade) {
        Optional<Integer> maxOpt = concursoRepository.findMaxNumeroByTipoLoteria(tipo);
        int ultimoConcurso = maxOpt.orElse(0);

        List<Object[]> ultimaAparicao = concursoRepository.findUltimaAparicaoDezenas(tipo.name());

        return ultimaAparicao.stream()
                .map(row -> new int[]{((Number) row[0]).intValue(), ultimoConcurso - ((Number) row[1]).intValue()})
                .sorted((a, b) -> Integer.compare(b[1], a[1]))
                .limit(quantidade)
                .map(arr -> arr[0])
                .collect(Collectors.toList());
    }

    private AnalisePatterns analisarPadroesRapido(TipoLoteria tipo, List<Object[]> freq) {
        Map<String, Long> faixas = new LinkedHashMap<>();

        Set<Integer> primos = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97);
        int inicio = tipo.getNumeroInicial();
        int metade = inicio + tipo.getNumerosDezenas() / 2;

        long totalPares = 0, totalImpares = 0, totalPrimos = 0, totalBaixos = 0, totalAltos = 0;
        long totalFreq = 0;

        for (Object[] row : freq) {
            int num = ((Number) row[0]).intValue();
            long f = ((Number) row[1]).longValue();
            totalFreq += f;

            if (num % 2 == 0) totalPares += f;
            else totalImpares += f;
            if (primos.contains(num)) totalPrimos += f;
            if (num <= metade) totalBaixos += f;
            else totalAltos += f;

            int faixaIdx = (num - inicio) / 10;
            String faixaKey = String.format("%02d-%02d", inicio + faixaIdx * 10, Math.min(inicio + (faixaIdx + 1) * 10 - 1, tipo.getNumeroFinal()));
            faixas.merge(faixaKey, f, Long::sum);
        }

        long totalConcursos = concursoRepository.countByTipoLoteria(tipo);
        double n = totalConcursos > 0 ? totalConcursos : 1;

        return new AnalisePatterns(
                round(totalPares / n), round(totalImpares / n),
                round(totalPrimos / n), 0,
                faixas, 0,
                round(totalBaixos / n), round(totalAltos / n)
        );
    }

    private ProximoConcursoInfo estimarProximoConcurso(Concurso ultimo, TipoLoteria tipo) {
        int proximoNumero = ultimo.getNumero() + 1;
        LocalDate proximaData = ultimo.getDataApuracao() != null
                ? calcularProximaDataSorteio(ultimo.getDataApuracao(), tipo)
                : LocalDate.now();

        boolean acumulado = ultimo.getAcumulado() != null && ultimo.getAcumulado();
        BigDecimal premioEstimado = ultimo.getValorAcumuladoProximoConcurso();
        if (premioEstimado == null) {
            premioEstimado = BigDecimal.ZERO;
        }

        return new ProximoConcursoInfo(proximoNumero, proximaData, premioEstimado, acumulado);
    }

    private LocalDate calcularProximaDataSorteio(LocalDate dataAtual, TipoLoteria tipo) {
        Set<DayOfWeek> diasSorteio = getDiasSorteio(tipo);
        LocalDate proxima = dataAtual.plusDays(1);
        
        // Avança até encontrar um dia válido de sorteio (máximo 7 dias)
        for (int i = 0; i < 7; i++) {
            if (diasSorteio.contains(proxima.getDayOfWeek())) {
                return proxima;
            }
            proxima = proxima.plusDays(1);
        }
        return proxima;
    }

    private Set<DayOfWeek> getDiasSorteio(TipoLoteria tipo) {
        return switch (tipo) {
            // Mega-Sena: Terça, Quinta e Sábado
            case MEGA_SENA -> Set.of(
                DayOfWeek.TUESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.SATURDAY
            );
            // Lotofácil: Segunda a Sábado
            case LOTOFACIL -> Set.of(
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY, 
                DayOfWeek.SATURDAY
            );
            // Quina: Segunda a Sábado
            case QUINA -> Set.of(
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY, 
                DayOfWeek.SATURDAY
            );
            // Lotomania: Segunda, Quarta e Sexta
            case LOTOMANIA -> Set.of(
                DayOfWeek.MONDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.FRIDAY
            );
            // Timemania: Terça, Quinta e Sábado
            case TIMEMANIA -> Set.of(
                DayOfWeek.TUESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.SATURDAY
            );
            // Dupla Sena: Segunda, Quarta e Sexta
            case DUPLA_SENA -> Set.of(
                DayOfWeek.MONDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.FRIDAY
            );
            // Dia de Sorte: Terça, Quinta e Sábado
            case DIA_DE_SORTE -> Set.of(
                DayOfWeek.TUESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.SATURDAY
            );
            // Super Sete: Segunda, Quarta e Sexta
            case SUPER_SETE -> Set.of(
                DayOfWeek.MONDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.FRIDAY
            );
            // +Milionária: Quarta e Sábado
            case MAIS_MILIONARIA -> Set.of(
                DayOfWeek.WEDNESDAY,
                DayOfWeek.SATURDAY
            );
        };
    }

    @Cacheable(value = CacheConfig.CACHE_DASHBOARD, key = "'acumulado-' + #tipo.name()")
    @Transactional(readOnly = true)
    public AcumuladoResponse getAcumulado(TipoLoteria tipo) {
        Optional<Concurso> ultimoOpt = concursoRepository.findTopByTipoLoteriaOrderByNumeroDescSimple(tipo.name());
        if (ultimoOpt.isEmpty()) {
            return new AcumuladoResponse(
                    tipo.name(), tipo.getNome(), false,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    0, 0, null, null);
        }

        Concurso ultimo = ultimoOpt.get();
        boolean acumulado = ultimo.getAcumulado() != null && ultimo.getAcumulado();
        BigDecimal valorAcumulado = ultimo.getValorAcumuladoProximoConcurso() != null
                ? ultimo.getValorAcumuladoProximoConcurso() : BigDecimal.ZERO;
        BigDecimal valorEstimado = ultimo.getValorEstimadoProximoConcurso() != null
                ? ultimo.getValorEstimadoProximoConcurso() : valorAcumulado;

        // Count consecutive accumulations
        List<Object[]> ultimos = concursoRepository.findUltimosAcumulados(tipo.name(), 100);
        int consecutivos = 0;
        for (Object[] row : ultimos) {
            Boolean acum = (Boolean) row[1];
            if (acum != null && acum) {
                consecutivos++;
            } else {
                break;
            }
        }

        LocalDate dataEstimada = ultimo.getDataApuracao() != null
                ? calcularProximaDataSorteio(ultimo.getDataApuracao(), tipo) : null;

        return new AcumuladoResponse(
                tipo.name(), tipo.getNome(), acumulado, valorAcumulado, valorEstimado,
                consecutivos, ultimo.getNumero(), ultimo.getDataApuracao(), dataEstimada);
    }

    @Cacheable(value = CacheConfig.CACHE_DASHBOARD, key = "'ganhadores-uf-' + #tipo.name()")
    @Transactional(readOnly = true)
    public GanhadoresUFResponse getGanhadoresPorUF(TipoLoteria tipo) {
        long totalConcursos = concursoRepository.countByTipoLoteria(tipo);
        List<Object[]> porUFCidade = concursoRepository.findGanhadoresPorUFCidade(tipo.name());
        List<Object[]> porUF = concursoRepository.findGanhadoresPorUF(tipo.name());
        String cidadesDesde = concursoRepository.findEarliestConcursoWithCityName(tipo.name())
                .map(LocalDate::toString)
                .orElse(null);

        // Build city details per state, merging entries that differ only by accents
        Map<String, Map<String, int[]>> cidadesMergePorEstado = new HashMap<>();
        Map<String, Map<String, String>> cidadesPreferredName = new HashMap<>();
        for (Object[] row : porUFCidade) {
            String uf = (String) row[0];
            String cidade = row[1] != null ? ((String) row[1]).trim() : "";
            int total = ((Number) row[2]).intValue();
            String normalizedKey = normalizeCityName(cidade);

            cidadesMergePorEstado.computeIfAbsent(uf, k -> new LinkedHashMap<>());
            cidadesPreferredName.computeIfAbsent(uf, k -> new HashMap<>());

            cidadesMergePorEstado.get(uf).merge(normalizedKey, new int[]{total}, (a, b) -> { a[0] += b[0]; return a; });
            // Prefer the accented (longer) form as display name
            cidadesPreferredName.get(uf).merge(normalizedKey, cidade, (existing, incoming) ->
                    incoming.length() > existing.length() ? incoming : existing);
        }

        Map<String, List<GanhadoresUFResponse.CidadeGanhadores>> cidadesPorEstado = new HashMap<>();
        cidadesMergePorEstado.forEach((uf, cityMap) -> {
            List<GanhadoresUFResponse.CidadeGanhadores> list = new ArrayList<>();
            cityMap.forEach((normalizedKey, totalArr) -> {
                String displayName = cidadesPreferredName.get(uf).getOrDefault(normalizedKey, normalizedKey);
                if (displayName.isEmpty()) displayName = "Não informada";
                list.add(new GanhadoresUFResponse.CidadeGanhadores(displayName, totalArr[0]));
            });
            cidadesPorEstado.put(uf, list);
        });

        int totalGanhadores = 0;
        List<GanhadoresUFResponse.EstadoGanhadores> estados = new ArrayList<>();
        for (Object[] row : porUF) {
            String uf = (String) row[0];
            int total = ((Number) row[1]).intValue();
            int concursos = ((Number) row[2]).intValue();
            totalGanhadores += total;

            List<GanhadoresUFResponse.CidadeGanhadores> cidades =
                    cidadesPorEstado.getOrDefault(uf, List.of());
            // Sort cities by winners desc, limit to top 10
            cidades = cidades.stream()
                    .sorted((a, b) -> Integer.compare(b.totalGanhadores(), a.totalGanhadores()))
                    .limit(10)
                    .collect(Collectors.toList());

            estados.add(new GanhadoresUFResponse.EstadoGanhadores(
                    uf, total, concursos, cidades));
        }

        return new GanhadoresUFResponse(
                tipo.name(), tipo.getNome(), (int) totalConcursos, totalGanhadores, cidadesDesde, estados);
    }

    private static String normalizeCityName(String name) {
        if (name == null || name.isBlank()) return "";
        String normalized = Normalizer.normalize(name.trim().toUpperCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private DashboardResponse criarDashboardVazio(TipoLoteria tipo) {
        return new DashboardResponse(
                tipo, tipo.getNome(),
                new ResumoGeral(0, null, null, 0, BigDecimal.ZERO, 0, 0),
                null, null, List.of(), List.of(), List.of(),
                new AnalisePatterns(0, 0, 0, 0, Map.of(), 0, 0, 0),
                null, null
        );
    }
}
