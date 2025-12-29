package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.domain.dto.CaixaApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ConcursoSyncService {

    private static final Logger log = LoggerFactory.getLogger(ConcursoSyncService.class);

    private final CaixaApiClient caixaApiClient;
    private final ConcursoMapper concursoMapper;
    private final ConcursoRepository concursoRepository;
    private final CacheConfig cacheConfig;
    private final ConcurrentHashMap<TipoLoteria, AtomicBoolean> syncLocks = new ConcurrentHashMap<>();

    public ConcursoSyncService(CaixaApiClient caixaApiClient, ConcursoMapper concursoMapper,
                               ConcursoRepository concursoRepository, CacheConfig cacheConfig) {
        this.caixaApiClient = caixaApiClient;
        this.concursoMapper = concursoMapper;
        this.concursoRepository = concursoRepository;
        this.cacheConfig = cacheConfig;
    }

    private void acquireSyncLock(TipoLoteria tipo) {
        AtomicBoolean lock = syncLocks.computeIfAbsent(tipo, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            throw new IllegalStateException("Sincronização de " + tipo.getNome() + " já em andamento. Aguarde a conclusão.");
        }
    }

    private void releaseSyncLock(TipoLoteria tipo) {
        AtomicBoolean lock = syncLocks.get(tipo);
        if (lock != null) {
            lock.set(false);
        }
    }

    public boolean isSyncInProgress() {
        return syncLocks.values().stream().anyMatch(AtomicBoolean::get);
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    public int sincronizarTodosConcursos(TipoLoteria tipo) {
        acquireSyncLock(tipo);
        try {
            return doSincronizarTodos(tipo);
        } finally {
            releaseSyncLock(tipo);
        }
    }

    private int doSincronizarTodos(TipoLoteria tipo) {
        log.info("Iniciando sincronização completa para {}", tipo.getNome());

        Optional<CaixaApiResponse> ultimoResponse = caixaApiClient.buscarUltimoConcurso(tipo);
        if (ultimoResponse.isEmpty()) {
            log.error("Não foi possível obter o último concurso de {}. Sincronização abortada.", tipo.getNome());
            return 0;
        }

        int ultimoNumero = ultimoResponse.get().numero();
        log.info("Último concurso de {} é o número {}", tipo.getNome(), ultimoNumero);

        int sincronizados = 0;
        int erros = 0;

        for (int numero = 1; numero <= ultimoNumero; numero++) {
            if (concursoRepository.existsByTipoLoteriaAndNumero(tipo, numero)) {
                log.debug("Concurso {} de {} já existe no banco", numero, tipo.getNome());
                continue;
            }

            try {
                Optional<CaixaApiResponse> response = caixaApiClient.buscarConcurso(tipo, numero);
                if (response.isPresent()) {
                    Concurso concurso = concursoMapper.toEntity(response.get(), tipo);
                    concursoRepository.save(concurso);
                    sincronizados++;
                    
                    if (sincronizados % 100 == 0) {
                        log.info("Progresso {}: {} concursos sincronizados", tipo.getNome(), sincronizados);
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao sincronizar concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
                erros++;
            }

            pauseEntreRequisicoes();
        }

        log.info("Sincronização completa de {} finalizada. Sincronizados: {}, Erros: {}",
                tipo.getNome(), sincronizados, erros);
        return sincronizados;
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    public int sincronizarNovosConcursos(TipoLoteria tipo) {
        acquireSyncLock(tipo);
        try {
            return doSincronizarNovos(tipo);
        } finally {
            releaseSyncLock(tipo);
        }
    }

    public Map<String, Integer> sincronizarTodosNovosConcursos() {
        Map<String, Integer> resultados = new LinkedHashMap<>();
        for (TipoLoteria tipo : TipoLoteria.values()) {
            try {
                resultados.put(tipo.getNome(), sincronizarNovosConcursos(tipo));
            } catch (IllegalStateException e) {
                log.warn("Skipping {} sync: {}", tipo.getNome(), e.getMessage());
                resultados.put(tipo.getNome(), 0);
            }
        }
        cacheConfig.evictAllCachesNow();
        return resultados;
    }

    private int doSincronizarNovos(TipoLoteria tipo) {
        log.info("Iniciando sincronização de novos concursos para {}", tipo.getNome());

        Optional<CaixaApiResponse> ultimoResponse = caixaApiClient.buscarUltimoConcurso(tipo);
        if (ultimoResponse.isEmpty()) {
            log.error("Não foi possível obter o último concurso de {}. Sincronização abortada.", tipo.getNome());
            return 0;
        }

        int ultimoNumeroApi = ultimoResponse.get().numero();
        int ultimoNumeroBanco = concursoRepository.findMaxNumeroByTipoLoteria(tipo).orElse(0);

        log.info("{}: último no banco = {}, último na API = {}",
                tipo.getNome(), ultimoNumeroBanco, ultimoNumeroApi);

        if (ultimoNumeroBanco >= ultimoNumeroApi) {
            log.info("{} já está atualizado", tipo.getNome());
            return 0;
        }

        int sincronizados = 0;

        for (int numero = ultimoNumeroBanco + 1; numero <= ultimoNumeroApi; numero++) {
            try {
                Optional<CaixaApiResponse> response = caixaApiClient.buscarConcurso(tipo, numero);
                if (response.isPresent()) {
                    Concurso concurso = concursoMapper.toEntity(response.get(), tipo);
                    concursoRepository.save(concurso);
                    sincronizados++;
                    log.debug("Concurso {} de {} sincronizado com sucesso", numero, tipo.getNome());
                }
            } catch (Exception e) {
                log.error("Erro ao sincronizar concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
            }

            pauseEntreRequisicoes();
        }

        log.info("Sincronização de novos concursos de {} finalizada. Sincronizados: {}",
                tipo.getNome(), sincronizados);
        return sincronizados;
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public SyncResult sincronizarUltimoConcurso(TipoLoteria tipo) {
        log.info("Sincronizando último concurso de {}", tipo.getNome());

        Optional<CaixaApiResponse> ultimoResponse = caixaApiClient.buscarUltimoConcurso(tipo);
        if (ultimoResponse.isEmpty()) {
            log.warn("Não foi possível obter o último concurso de {}", tipo.getNome());
            return new SyncResult(tipo.getNome(), 0, false, "Erro ao buscar na API");
        }

        CaixaApiResponse response = ultimoResponse.get();
        int numeroApi = response.numero();
        
        try {
            // Busca concurso existente ou cria novo
            Optional<Concurso> existente = concursoRepository.findByTipoLoteriaAndNumero(tipo, numeroApi);
            
            if (existente.isPresent()) {
                // Atualiza o concurso existente com dados mais recentes
                Concurso concurso = existente.get();
                concursoMapper.updateEntity(concurso, response);
                concursoRepository.save(concurso);
                log.info("Concurso {} de {} atualizado com dados mais recentes", numeroApi, tipo.getNome());
                return new SyncResult(tipo.getNome(), 1, true, "Concurso " + numeroApi + " atualizado");
            } else {
                // Cria novo concurso
                Concurso concurso = concursoMapper.toEntity(response, tipo);
                concursoRepository.save(concurso);
                log.info("Concurso {} de {} sincronizado com sucesso", numeroApi, tipo.getNome());
                return new SyncResult(tipo.getNome(), 1, true, "Concurso " + numeroApi + " sincronizado");
            }
        } catch (Exception e) {
            log.error("Erro ao salvar concurso {} de {}: {}", numeroApi, tipo.getNome(), e.getMessage());
            return new SyncResult(tipo.getNome(), 0, false, "Erro ao salvar: " + e.getMessage());
        }
    }

    public Map<String, SyncResult> sincronizarUltimosConcursosTodos() {
        return doSincronizarUltimosTodos();
    }

    private Map<String, SyncResult> doSincronizarUltimosTodos() {
        log.info("Sincronizando último concurso de todas as loterias (paralelo)");
        
        Map<String, SyncResult> resultados = new ConcurrentHashMap<>();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Limit concurrency to avoid overwhelming Caixa API
            var semaphore = new Semaphore(3);
            var futures = Arrays.stream(TipoLoteria.values())
                .map(tipo -> CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            SyncResult result = sincronizarUltimoConcurso(tipo);
                            resultados.put(tipo.getNome(), result);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        resultados.put(tipo.getNome(), new SyncResult(tipo.getNome(), 0, false, "Interrupted"));
                    } catch (Exception e) {
                        log.error("Erro ao sincronizar {}: {}", tipo.getNome(), e.getMessage());
                        resultados.put(tipo.getNome(), new SyncResult(tipo.getNome(), 0, false, e.getMessage()));
                    }
                }, executor))
                .toList();
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        cacheConfig.evictAllCachesNow();
        
        int totalSincronizados = resultados.values().stream().mapToInt(SyncResult::sincronizados).sum();
        log.info("Sincronização de últimos concursos finalizada. Total sincronizados: {}", totalSincronizados);
        
        return resultados;
    }

    private void pauseEntreRequisicoes() {
        pauseEntreRequisicoes(100);
    }

    private void pauseEntreRequisicoes(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SyncResult(String loteria, int sincronizados, boolean sucesso, String mensagem) {}

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    public int sincronizarUltimosDias(TipoLoteria tipo, int dias) {
        acquireSyncLock(tipo);
        try {
            return doSincronizarUltimosDias(tipo, dias);
        } finally {
            releaseSyncLock(tipo);
        }
    }

    private int doSincronizarUltimosDias(TipoLoteria tipo, int dias) {
        log.info("Sincronizando últimos {} dias de {}", dias, tipo.getNome());

        Optional<CaixaApiResponse> ultimoResponse = caixaApiClient.buscarUltimoConcurso(tipo);
        if (ultimoResponse.isEmpty()) {
            log.error("Não foi possível obter o último concurso de {}. Abortando.", tipo.getNome());
            return 0;
        }

        int ultimoNumeroApi = ultimoResponse.get().numero();

        Optional<Integer> minNumero = concursoRepository.findMinNumeroDesde(tipo.name(), dias);

        int inicio;
        if (minNumero.isPresent()) {
            inicio = minNumero.get();
        } else {
            // No data from last N days in DB — estimate conservatively (1 draw/day)
            inicio = Math.max(1, ultimoNumeroApi - dias);
        }

        log.info("{}: atualizando concursos {} a {} (últimos {} dias)",
                tipo.getNome(), inicio, ultimoNumeroApi, dias);

        int sincronizados = 0;
        int erros = 0;

        for (int numero = inicio; numero <= ultimoNumeroApi; numero++) {
            try {
                Optional<CaixaApiResponse> response = caixaApiClient.buscarConcurso(tipo, numero);
                if (response.isPresent()) {
                    Optional<Concurso> existente = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
                    if (existente.isPresent()) {
                        concursoMapper.updateEntity(existente.get(), response.get());
                        concursoRepository.save(existente.get());
                    } else {
                        Concurso concurso = concursoMapper.toEntity(response.get(), tipo);
                        concursoRepository.save(concurso);
                    }
                    sincronizados++;
                }
            } catch (Exception e) {
                log.error("Erro ao sincronizar concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
                erros++;
            }
            pauseEntreRequisicoes();
        }

        log.info("{}: últimos {} dias sincronizados. Total: {}, Erros: {}",
                tipo.getNome(), dias, sincronizados, erros);
        return sincronizados;
    }

    public Map<String, Integer> sincronizarTodosUltimosDias(int dias) {
        log.info("Sincronizando últimos {} dias de TODAS as loterias", dias);
        Map<String, Integer> resultados = new LinkedHashMap<>();

        for (TipoLoteria tipo : TipoLoteria.values()) {
            try {
                resultados.put(tipo.getNome(), sincronizarUltimosDias(tipo, dias));
            } catch (IllegalStateException e) {
                log.warn("Skipping {} sync: {}", tipo.getNome(), e.getMessage());
                resultados.put(tipo.getNome(), 0);
            }
        }

        cacheConfig.evictAllCachesNow();
        int total = resultados.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Sincronização últimos {} dias finalizada. Total: {}", dias, total);
        return resultados;
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    @Transactional
    public BackfillResult backfillFaixasPremiacao(TipoLoteria tipo) {
        acquireSyncLock(tipo);
        try {
            return doBackfillFaixas(tipo);
        } finally {
            releaseSyncLock(tipo);
        }
    }

    private BackfillResult doBackfillFaixas(TipoLoteria tipo) {
        List<Integer> semFaixas = concursoRepository.findConcursosSemFaixas(tipo.name());

        if (semFaixas.isEmpty()) {
            log.info("{}: todos os concursos já possuem faixas de premiação", tipo.getNome());
            return new BackfillResult(tipo.getNome(), 0, 0, semFaixas.size());
        }

        log.info("{}: {} concursos sem faixas de premiação. Iniciando backfill...", tipo.getNome(), semFaixas.size());

        int atualizados = 0;
        int erros = 0;

        for (int numero : semFaixas) {
            try {
                Optional<CaixaApiResponse> response = caixaApiClient.buscarConcurso(tipo, numero);
                if (response.isPresent()) {
                    Optional<Concurso> existente = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
                    if (existente.isPresent()) {
                        concursoMapper.updateEntity(existente.get(), response.get());
                        concursoRepository.save(existente.get());
                        atualizados++;

                        if (atualizados % 100 == 0) {
                            log.info("{}: backfill progresso - {}/{} atualizados", tipo.getNome(), atualizados, semFaixas.size());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Erro no backfill do concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
                erros++;
            }
            pauseEntreRequisicoes();
        }

        log.info("{}: backfill finalizado. Atualizados: {}, Erros: {}", tipo.getNome(), atualizados, erros);
        return new BackfillResult(tipo.getNome(), atualizados, erros, semFaixas.size());
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    public Map<String, BackfillResult> backfillFaixasTodos() {
        log.info("Backfill de faixas de premiação para TODAS as loterias");
        Map<String, BackfillResult> resultados = new LinkedHashMap<>();

        for (TipoLoteria tipo : TipoLoteria.values()) {
            try {
                resultados.put(tipo.getNome(), backfillFaixasPremiacao(tipo));
            } catch (IllegalStateException e) {
                log.warn("Skipping {} backfill: {}", tipo.getNome(), e.getMessage());
                resultados.put(tipo.getNome(), new BackfillResult(tipo.getNome(), 0, 0, 0));
            }
        }

        cacheConfig.evictAllCachesNow();
        int totalAtualizados = resultados.values().stream().mapToInt(BackfillResult::atualizados).sum();
        log.info("Backfill de faixas finalizado. Total atualizados: {}", totalAtualizados);
        return resultados;
    }

    public Map<String, Object> getBackfillStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        List<Object[]> counts = concursoRepository.countConcursosIncompletosPorTipo();
        int totalIncompletos = 0;
        List<Map<String, Object>> loterias = new ArrayList<>();

        for (Object[] row : counts) {
            String tipoStr = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long semFaixas = ((Number) row[2]).longValue();
            long semArrecadacao = ((Number) row[3]).longValue();
            long incompletos = ((Number) row[4]).longValue();
            if (incompletos > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tipo", tipoStr);
                item.put("total", total);
                item.put("semFaixas", semFaixas);
                item.put("semArrecadacao", semArrecadacao);
                item.put("incompletos", incompletos);
                item.put("percentual", total > 0 ? Math.round(incompletos * 100.0 / total) : 0);
                loterias.add(item);
                totalIncompletos += incompletos;
            }
        }

        status.put("totalIncompletos", totalIncompletos);
        status.put("loterias", loterias);
        return status;
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    @Transactional
    public BackfillResult backfillIncompletos(TipoLoteria tipo) {
        acquireSyncLock(tipo);
        try {
            return doBackfillIncompletos(tipo);
        } finally {
            releaseSyncLock(tipo);
        }
    }

    private BackfillResult doBackfillIncompletos(TipoLoteria tipo) {
        List<Integer> incompletos = concursoRepository.findConcursosIncompletos(tipo.name());

        if (incompletos.isEmpty()) {
            log.info("{}: todos os concursos estão completos", tipo.getNome());
            return new BackfillResult(tipo.getNome(), 0, 0, 0);
        }

        log.info("{}: {} concursos incompletos (sem faixas ou sem arrecadação). Iniciando backfill...",
                tipo.getNome(), incompletos.size());

        int atualizados = 0;
        int erros = 0;

        for (int numero : incompletos) {
            try {
                Optional<CaixaApiResponse> response = caixaApiClient.buscarConcurso(tipo, numero);
                if (response.isPresent()) {
                    Optional<Concurso> existente = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
                    if (existente.isPresent()) {
                        concursoMapper.updateEntity(existente.get(), response.get());
                        concursoRepository.save(existente.get());
                        atualizados++;

                        if (atualizados % 200 == 0) {
                            log.info("{}: backfill progresso - {}/{} atualizados", tipo.getNome(), atualizados, incompletos.size());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Erro no backfill do concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
                erros++;
                if (erros > 10 && erros > atualizados / 5) {
                    log.error("{}: muitos erros ({}/{}), abortando backfill", tipo.getNome(), erros, atualizados);
                    break;
                }
            }
            pauseEntreRequisicoes(250);
        }

        log.info("{}: backfill completo finalizado. Atualizados: {}, Erros: {}", tipo.getNome(), atualizados, erros);
        return new BackfillResult(tipo.getNome(), atualizados, erros, incompletos.size());
    }

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ESTATISTICAS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TIME_CORACAO, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_ESPECIAIS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_FINANCEIRO, allEntries = true)
    })
    public Map<String, BackfillResult> backfillIncompletosTodos() {
        log.info("Backfill completo de TODAS as loterias (faixas + arrecadação)");
        Map<String, BackfillResult> resultados = new LinkedHashMap<>();

        for (TipoLoteria tipo : TipoLoteria.values()) {
            try {
                resultados.put(tipo.getNome(), backfillIncompletos(tipo));
            } catch (IllegalStateException e) {
                log.warn("Skipping {} backfill: {}", tipo.getNome(), e.getMessage());
                resultados.put(tipo.getNome(), new BackfillResult(tipo.getNome(), 0, 0, 0));
            }
        }

        cacheConfig.evictAllCachesNow();
        int totalAtualizados = resultados.values().stream().mapToInt(BackfillResult::atualizados).sum();
        log.info("Backfill completo finalizado. Total atualizados: {}", totalAtualizados);
        return resultados;
    }

    public record BackfillResult(String loteria, int atualizados, int erros, int totalSemFaixas) {}
}
