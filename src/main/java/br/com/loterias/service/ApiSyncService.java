package br.com.loterias.service;

import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import br.com.loterias.domain.repository.ConcursoRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class ApiSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApiSyncService.class);

    private static final long BASE_DELAY_MS = 500;
    private static final long MIN_DELAY_BETWEEN_REQUESTS_MS = 200;
    private static final long MAX_BACKOFF_MS = Duration.ofMinutes(35).toMillis();
    private static final long INITIAL_BACKOFF_MS = Duration.ofSeconds(5).toMillis();
    private static final int MAX_CONSECUTIVE_429_ERRORS = 10;
    private static final int BATCH_SIZE = 50;

    private final CaixaApiClient caixaApiClient;
    private final ConcursoMapper concursoMapper;
    private final ConcursoBatchService batchService;
    private final ConcursoRepository concursoRepository;

    private final Map<String, SyncProgress> progressMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Map<TipoLoteria, SyncResult>>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SyncResult>> runningSingleTasks = new ConcurrentHashMap<>();
    private final AtomicLong currentBackoffMs = new AtomicLong(INITIAL_BACKOFF_MS);
    private final AtomicInteger consecutive429Errors = new AtomicInteger(0);
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ApiSyncService(
            CaixaApiClient caixaApiClient,
            ConcursoMapper concursoMapper,
            ConcursoBatchService batchService,
            ConcursoRepository concursoRepository) {
        this.caixaApiClient = caixaApiClient;
        this.concursoMapper = concursoMapper;
        this.batchService = batchService;
        this.concursoRepository = concursoRepository;
    }

    @PreDestroy
    void shutdown() {
        virtualExecutor.close();
    }

    public String iniciarSincronizacaoTodosAsync() {
        String taskId = "sync-all-" + System.currentTimeMillis();
        
        CompletableFuture<Map<TipoLoteria, SyncResult>> future = CompletableFuture.supplyAsync(() -> {
            log.info("[{}] Iniciando sincronização completa de todas as loterias via API", taskId);
            
            Map<TipoLoteria, SyncResult> resultados = new LinkedHashMap<>();
            
            for (TipoLoteria tipo : TipoLoteria.values()) {
                try {
                    SyncResult result = sincronizarLoteria(tipo, taskId);
                    resultados.put(tipo, result);
                    
                    pauseEntreLoterias();
                } catch (Exception e) {
                    log.error("[{}] Erro ao sincronizar {}: {}", taskId, tipo.getNome(), e.getMessage());
                    resultados.put(tipo, SyncResult.error(tipo.getNome(), e.getMessage()));
                }
            }
            
            logResumoFinal(resultados);
            runningTasks.remove(taskId);
            return resultados;
        }, virtualExecutor);
        
        runningTasks.put(taskId, future);
        return taskId;
    }

    public String iniciarSincronizacaoLoteriaAsync(TipoLoteria tipo) {
        String taskId = "sync-" + tipo.name().toLowerCase() + "-" + System.currentTimeMillis();
        
        CompletableFuture<SyncResult> future = CompletableFuture.supplyAsync(() -> {
            SyncResult result = sincronizarLoteria(tipo, taskId);
            runningSingleTasks.remove(taskId);
            return result;
        }, virtualExecutor);
        
        runningSingleTasks.put(taskId, future);
        return taskId;
    }

    public Optional<Map<TipoLoteria, SyncResult>> getTaskResult(String taskId) {
        CompletableFuture<Map<TipoLoteria, SyncResult>> future = runningTasks.get(taskId);
        if (future != null && future.isDone()) {
            try {
                return Optional.of(future.get());
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<SyncResult> getSingleTaskResult(String taskId) {
        CompletableFuture<SyncResult> future = runningSingleTasks.get(taskId);
        if (future != null && future.isDone()) {
            try {
                return Optional.of(future.get());
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean isTaskRunning(String taskId) {
        CompletableFuture<?> future = runningTasks.get(taskId);
        if (future == null) {
            future = runningSingleTasks.get(taskId);
        }
        return future != null && !future.isDone();
    }

    public SyncResult sincronizarLoteria(TipoLoteria tipo) {
        return sincronizarLoteria(tipo, tipo.name() + "-" + System.currentTimeMillis());
    }

    public SyncResult sincronizarLoteria(TipoLoteria tipo, String taskId) {
        Instant inicio = Instant.now();
        
        log.info("[{}] Iniciando sincronização/atualização completa via API...", tipo.getNome());
        
        Optional<CaixaApiResponse> ultimoResponse = fetchWithRateLimiting(() -> 
                caixaApiClient.buscarUltimoConcurso(tipo), tipo.getNome(), "último concurso");
        
        if (ultimoResponse.isEmpty()) {
            log.error("[{}] Não foi possível obter o último concurso. Abortando.", tipo.getNome());
            return SyncResult.error(tipo.getNome(), "Falha ao obter último concurso da API");
        }
        
        int ultimoNumero = ultimoResponse.get().numero();
        Set<Integer> numerosExistentes = new HashSet<>(batchService.findNumerosByTipoLoteria(tipo));
        
        log.info("[{}] Último concurso: {}. Existentes no banco: {}", 
                tipo.getNome(), ultimoNumero, numerosExistentes.size());
        
        List<Integer> todosNumeros = new ArrayList<>();
        for (int i = 1; i <= ultimoNumero; i++) {
            todosNumeros.add(i);
        }
        
        log.info("[{}] Processando {} concursos (novos + atualizações)", tipo.getNome(), todosNumeros.size());
        
        SyncProgress progress = new SyncProgress(tipo.getNome(), todosNumeros.size());
        progressMap.put(taskId, progress);
        
        AtomicInteger novos = new AtomicInteger(0);
        AtomicInteger atualizados = new AtomicInteger(0);
        AtomicInteger semAlteracao = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);
        
        for (int numero : todosNumeros) {
            try {
                Optional<CaixaApiResponse> response = fetchWithRateLimiting(() -> 
                        caixaApiClient.buscarConcurso(tipo, numero), tipo.getNome(), "concurso " + numero);
                
                if (response.isPresent()) {
                    CaixaApiResponse apiData = response.get();
                    
                    if (numerosExistentes.contains(numero)) {
                        boolean updated = atualizarConcursoExistente(tipo, numero, apiData);
                        if (updated) {
                            atualizados.incrementAndGet();
                        } else {
                            semAlteracao.incrementAndGet();
                        }
                    } else {
                        Concurso concurso = concursoMapper.toEntity(apiData, tipo);
                        batchService.salvarConcurso(concurso);
                        numerosExistentes.add(numero);
                        novos.incrementAndGet();
                    }
                    
                    progress.incrementProcessed();
                    
                    int total = novos.get() + atualizados.get();
                    if (total > 0 && total % 100 == 0) {
                        log.info("[{}] Progresso: {}/{} processados ({} novos, {} atualizados, {} erros)", 
                                tipo.getNome(), progress.getProcessed(), todosNumeros.size(), 
                                novos.get(), atualizados.get(), erros.get());
                    }
                } else {
                    erros.incrementAndGet();
                    progress.incrementErrors();
                }
                
                pauseEntreRequisicoes();
                
            } catch (SyncAbortedException e) {
                log.error("[{}] Sincronização abortada: {}", tipo.getNome(), e.getMessage());
                return SyncResult.error(tipo.getNome(), "Abortado: " + e.getMessage());
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("[{}] Erro ao processar concurso {}: {}", tipo.getNome(), numero, errorMsg);
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Stack trace do erro no concurso {}", tipo.getNome(), numero, e);
                }
                erros.incrementAndGet();
                progress.incrementErrors();
            }
        }
        
        long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
        progressMap.remove(taskId);
        
        log.info("[{}] Sincronização finalizada. Novos: {}, Atualizados: {}, Sem alteração: {}, Erros: {}, Tempo: {}ms",
                tipo.getNome(), novos.get(), atualizados.get(), semAlteracao.get(), erros.get(), tempoMs);
        
        return new SyncResult(tipo.getNome(), novos.get(), atualizados.get(), erros.get(), 
                semAlteracao.get(), tempoMs, null);
    }

    private boolean atualizarConcursoExistente(TipoLoteria tipo, int numero, CaixaApiResponse apiData) {
        return batchService.atualizarConcursoComDadosApi(tipo, numero, apiData);
    }

    /**
     * Limpa dados incorretos e atualiza todos os concursos com dados corretos da API.
     * Usado para corrigir dados que foram importados incorretamente do Excel.
     */
    public String iniciarCorrecaoDadosAsync() {
        String taskId = "fix-data-" + System.currentTimeMillis();
        
        CompletableFuture<Map<TipoLoteria, SyncResult>> future = CompletableFuture.supplyAsync(() -> {
            log.info("[{}] Iniciando correção de dados (limpar + atualizar)", taskId);
            
            Map<TipoLoteria, SyncResult> resultados = new LinkedHashMap<>();
            
            for (TipoLoteria tipo : TipoLoteria.values()) {
                try {
                    SyncResult result = corrigirDadosLoteria(tipo, taskId);
                    resultados.put(tipo, result);
                    pauseEntreLoterias();
                } catch (Exception e) {
                    log.error("[{}] Erro ao corrigir {}: {}", taskId, tipo.getNome(), e.getMessage());
                    resultados.put(tipo, SyncResult.error(tipo.getNome(), e.getMessage()));
                }
            }
            
            logResumoFinal(resultados);
            runningTasks.remove(taskId);
            return resultados;
        }, virtualExecutor);
        
        runningTasks.put(taskId, future);
        return taskId;
    }

    @org.springframework.transaction.annotation.Transactional
    public SyncResult corrigirDadosLoteria(TipoLoteria tipo, String taskId) {
        Instant inicio = Instant.now();
        
        // Limpar dados incorretos
        int limpos = concursoRepository.limparLocalSorteio(tipo);
        log.info("[{}] Limpos {} registros de nomeMunicipioUFSorteio", tipo.getNome(), limpos);
        
        if (tipo == TipoLoteria.TIMEMANIA || tipo == TipoLoteria.DIA_DE_SORTE) {
            int limposTime = concursoRepository.limparTimeCoracaoMesSorte(tipo);
            log.info("[{}] Limpos {} registros de nomeTimeCoracaoMesSorte", tipo.getNome(), limposTime);
        }
        
        // Buscar todos os concursos para atualizar
        List<Integer> todosNumeros = concursoRepository.findAllNumerosByTipoLoteria(tipo);
        
        if (todosNumeros.isEmpty()) {
            log.info("[{}] Nenhum concurso encontrado", tipo.getNome());
            return new SyncResult(tipo.getNome(), 0, 0, 0, 0, 0, null);
        }
        
        log.info("[{}] Atualizando {} concursos com dados da API", tipo.getNome(), todosNumeros.size());
        
        SyncProgress progress = new SyncProgress(tipo.getNome(), todosNumeros.size());
        progressMap.put(taskId + "-" + tipo.name(), progress);
        
        AtomicInteger atualizados = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);
        
        for (int numero : todosNumeros) {
            try {
                Optional<CaixaApiResponse> response = fetchWithRateLimiting(() -> 
                        caixaApiClient.buscarConcurso(tipo, numero), tipo.getNome(), "concurso " + numero);
                
                if (response.isPresent()) {
                    boolean updated = atualizarConcursoExistente(tipo, numero, response.get());
                    if (updated) {
                        atualizados.incrementAndGet();
                    }
                } else {
                    erros.incrementAndGet();
                }
                
                progress.incrementProcessed();
                
                if (progress.getProcessed() % 100 == 0) {
                    log.info("[{}] Progresso: {}/{} processados, {} atualizados", 
                            tipo.getNome(), progress.getProcessed(), todosNumeros.size(), atualizados.get());
                }
                
                pauseEntreRequisicoes();
                
            } catch (SyncAbortedException e) {
                log.error("[{}] Correção abortada: {}", tipo.getNome(), e.getMessage());
                return SyncResult.error(tipo.getNome(), "Abortado: " + e.getMessage());
            } catch (Exception e) {
                log.warn("[{}] Erro ao atualizar concurso {}: {}", tipo.getNome(), numero, e.getMessage());
                erros.incrementAndGet();
                progress.incrementErrors();
            }
        }
        
        long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
        progressMap.remove(taskId + "-" + tipo.name());
        
        log.info("[{}] Correção concluída. Atualizados: {}, Erros: {}, Tempo: {}ms",
                tipo.getNome(), atualizados.get(), erros.get(), tempoMs);
        
        return new SyncResult(tipo.getNome(), 0, atualizados.get(), erros.get(), 
                todosNumeros.size() - atualizados.get() - erros.get(), tempoMs, null);
    }

    /**
     * Atualiza apenas concursos que tiveram ganhadores na faixa principal.
     * Útil para corrigir dados de local de sorteio que foram preenchidos incorretamente
     * com a cidade dos ganhadores.
     */
    public String iniciarCorrecaoGanhadoresAsync() {
        String taskId = "fix-winners-" + System.currentTimeMillis();
        
        CompletableFuture<Map<TipoLoteria, SyncResult>> future = CompletableFuture.supplyAsync(() -> {
            log.info("[{}] Iniciando correção de concursos com ganhadores", taskId);
            
            Map<TipoLoteria, SyncResult> resultados = new LinkedHashMap<>();
            
            for (TipoLoteria tipo : TipoLoteria.values()) {
                try {
                    SyncResult result = corrigirConcursosComGanhadores(tipo, taskId);
                    resultados.put(tipo, result);
                    pauseEntreLoterias();
                } catch (Exception e) {
                    log.error("[{}] Erro ao corrigir {}: {}", taskId, tipo.getNome(), e.getMessage());
                    resultados.put(tipo, SyncResult.error(tipo.getNome(), e.getMessage()));
                }
            }
            
            logResumoFinal(resultados);
            runningTasks.remove(taskId);
            return resultados;
        }, virtualExecutor);
        
        runningTasks.put(taskId, future);
        return taskId;
    }

    public SyncResult corrigirConcursosComGanhadores(TipoLoteria tipo, String taskId) {
        Instant inicio = Instant.now();
        
        // Buscar concursos com ganhadores na faixa principal
        List<Integer> concursosComGanhadores = concursoRepository.findConcursosComGanhadoresFaixaPrincipal(tipo.name());
        
        if (concursosComGanhadores.isEmpty()) {
            log.info("[{}] Nenhum concurso com ganhadores encontrado", tipo.getNome());
            return new SyncResult(tipo.getNome(), 0, 0, 0, 0, 0, null);
        }
        
        log.info("[{}] Encontrados {} concursos com ganhadores para atualizar", tipo.getNome(), concursosComGanhadores.size());
        
        SyncProgress progress = new SyncProgress(tipo.getNome(), concursosComGanhadores.size());
        progressMap.put(taskId + "-" + tipo.name(), progress);
        
        AtomicInteger atualizados = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);
        
        for (int numero : concursosComGanhadores) {
            try {
                Optional<CaixaApiResponse> response = fetchWithRateLimiting(() -> 
                        caixaApiClient.buscarConcurso(tipo, numero), tipo.getNome(), "concurso " + numero);
                
                if (response.isPresent()) {
                    // Forçar atualização do local de sorteio
                    batchService.forcarAtualizacaoLocalSorteio(tipo, numero, response.get());
                    atualizados.incrementAndGet();
                } else {
                    erros.incrementAndGet();
                }
                
                progress.incrementProcessed();
                
                if (progress.getProcessed() % 50 == 0) {
                    log.info("[{}] Progresso: {}/{} processados", 
                            tipo.getNome(), progress.getProcessed(), concursosComGanhadores.size());
                }
                
                pauseEntreRequisicoes();
                
            } catch (SyncAbortedException e) {
                log.error("[{}] Correção abortada: {}", tipo.getNome(), e.getMessage());
                return SyncResult.error(tipo.getNome(), "Abortado: " + e.getMessage());
            } catch (Exception e) {
                log.warn("[{}] Erro ao atualizar concurso {}: {}", tipo.getNome(), numero, e.getMessage());
                erros.incrementAndGet();
                progress.incrementErrors();
            }
        }
        
        long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
        progressMap.remove(taskId + "-" + tipo.name());
        
        log.info("[{}] Correção concluída. Atualizados: {}, Erros: {}, Tempo: {}ms",
                tipo.getNome(), atualizados.get(), erros.get(), tempoMs);
        
        return new SyncResult(tipo.getNome(), 0, atualizados.get(), erros.get(), 0, tempoMs, null);
    }

    /**
     * Atualiza apenas concursos com dados faltantes:
     * - nomeMunicipioUFSorteio (local do sorteio)
     * - nomeTimeCoracaoMesSorte (para Timemania e Dia de Sorte)
     */
    public String iniciarAtualizacaoDadosFaltantesAsync() {
        String taskId = "fix-missing-" + System.currentTimeMillis();
        
        CompletableFuture<Map<TipoLoteria, SyncResult>> future = CompletableFuture.supplyAsync(() -> {
            log.info("[{}] Iniciando atualização de dados faltantes (local sorteio + time coração)", taskId);
            
            Map<TipoLoteria, SyncResult> resultados = new LinkedHashMap<>();
            
            for (TipoLoteria tipo : TipoLoteria.values()) {
                try {
                    SyncResult result = atualizarDadosFaltantes(tipo, taskId);
                    resultados.put(tipo, result);
                    pauseEntreLoterias();
                } catch (Exception e) {
                    log.error("[{}] Erro ao atualizar {}: {}", taskId, tipo.getNome(), e.getMessage());
                    resultados.put(tipo, SyncResult.error(tipo.getNome(), e.getMessage()));
                }
            }
            
            logResumoFinal(resultados);
            runningTasks.remove(taskId);
            return resultados;
        }, virtualExecutor);
        
        runningTasks.put(taskId, future);
        return taskId;
    }

    public SyncResult atualizarDadosFaltantes(TipoLoteria tipo, String taskId) {
        Instant inicio = Instant.now();
        
        List<Integer> numerosFaltantes = new ArrayList<>();
        
        // Buscar concursos sem local de sorteio
        List<Integer> semLocalSorteio = concursoRepository.findNumerosComLocalSorteioFaltando(tipo);
        numerosFaltantes.addAll(semLocalSorteio);
        
        // Para Timemania e Dia de Sorte, buscar também os sem time/mês
        if (tipo == TipoLoteria.TIMEMANIA || tipo == TipoLoteria.DIA_DE_SORTE) {
            List<Integer> semTimeCoracao = concursoRepository.findNumerosComTimeCoracaoFaltando(tipo);
            for (Integer num : semTimeCoracao) {
                if (!numerosFaltantes.contains(num)) {
                    numerosFaltantes.add(num);
                }
            }
        }
        
        Collections.sort(numerosFaltantes);
        
        if (numerosFaltantes.isEmpty()) {
            log.info("[{}] Nenhum concurso com dados faltantes", tipo.getNome());
            return new SyncResult(tipo.getNome(), 0, 0, 0, 0, 0, null);
        }
        
        log.info("[{}] Encontrados {} concursos com dados faltantes", tipo.getNome(), numerosFaltantes.size());
        
        SyncProgress progress = new SyncProgress(tipo.getNome(), numerosFaltantes.size());
        progressMap.put(taskId + "-" + tipo.name(), progress);
        
        AtomicInteger atualizados = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);
        AtomicInteger semAlteracao = new AtomicInteger(0);
        
        for (int numero : numerosFaltantes) {
            try {
                Optional<CaixaApiResponse> response = fetchWithRateLimiting(() -> 
                        caixaApiClient.buscarConcurso(tipo, numero), tipo.getNome(), "concurso " + numero);
                
                if (response.isPresent()) {
                    boolean updated = atualizarConcursoExistente(tipo, numero, response.get());
                    if (updated) {
                        atualizados.incrementAndGet();
                    } else {
                        semAlteracao.incrementAndGet();
                    }
                } else {
                    erros.incrementAndGet();
                }
                
                progress.incrementProcessed();
                
                if (atualizados.get() > 0 && atualizados.get() % 50 == 0) {
                    log.info("[{}] Progresso: {}/{} atualizados", 
                            tipo.getNome(), atualizados.get(), numerosFaltantes.size());
                }
                
                pauseEntreRequisicoes();
                
            } catch (SyncAbortedException e) {
                log.error("[{}] Atualização abortada: {}", tipo.getNome(), e.getMessage());
                return SyncResult.error(tipo.getNome(), "Abortado: " + e.getMessage());
            } catch (Exception e) {
                log.warn("[{}] Erro ao atualizar concurso {}: {}", tipo.getNome(), numero, e.getMessage());
                erros.incrementAndGet();
                progress.incrementErrors();
            }
        }
        
        long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
        progressMap.remove(taskId + "-" + tipo.name());
        
        log.info("[{}] Atualização concluída. Atualizados: {}, Sem alteração: {}, Erros: {}, Tempo: {}ms",
                tipo.getNome(), atualizados.get(), semAlteracao.get(), erros.get(), tempoMs);
        
        return new SyncResult(tipo.getNome(), 0, atualizados.get(), erros.get(), 
                semAlteracao.get(), tempoMs, null);
    }

    private <T> Optional<T> fetchWithRateLimiting(Supplier<Optional<T>> fetcher, 
            String tipoNome, String descricao) {
        
        int tentativas = 0;
        long backoff = currentBackoffMs.get();
        
        while (tentativas < MAX_CONSECUTIVE_429_ERRORS) {
            tentativas++;
            
            try {
                Optional<T> result = fetcher.get();
                
                if (result.isPresent()) {
                    consecutive429Errors.set(0);
                    currentBackoffMs.set(INITIAL_BACKOFF_MS);
                    return result;
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                if (is429Error(e)) {
                    int errorCount = consecutive429Errors.incrementAndGet();
                    
                    if (errorCount >= MAX_CONSECUTIVE_429_ERRORS) {
                        log.error("Máximo de erros 429 consecutivos ({}) atingido. Abortando sincronização.", 
                                MAX_CONSECUTIVE_429_ERRORS);
                        throw new SyncAbortedException("Muitos erros 429 consecutivos - possível ban de IP");
                    }
                    
                    backoff = calculateBackoff(errorCount);
                    currentBackoffMs.set(backoff);
                    
                    log.warn("[{}] Rate limit (429) ao buscar {}. Erro #{}/{}. Aguardando {}...",
                            tipoNome, descricao, errorCount, MAX_CONSECUTIVE_429_ERRORS, formatDuration(backoff));
                    
                    sleep(backoff);
                    
                } else if (isRetryableError(e) && tentativas < 3) {
                    log.warn("[{}] Erro retentável ao buscar {}: {}. Tentativa {}/3",
                            tipoNome, descricao, e.getMessage(), tentativas);
                    sleep(BASE_DELAY_MS * tentativas);
                } else {
                    log.error("[{}] Erro não retentável ao buscar {}: {}", tipoNome, descricao, e.getMessage());
                    return Optional.empty();
                }
            }
        }
        
        return Optional.empty();
    }

    private long calculateBackoff(int errorCount) {
        long baseBackoff = INITIAL_BACKOFF_MS * (long) Math.pow(2, errorCount - 1);
        long jitter = ThreadLocalRandom.current().nextLong(0, baseBackoff / 4);
        long totalBackoff = baseBackoff + jitter;
        
        if (errorCount >= 3) {
            totalBackoff = Math.min(totalBackoff * 2, MAX_BACKOFF_MS);
        }
        if (errorCount >= 5) {
            totalBackoff = Math.min(Duration.ofMinutes(15).toMillis(), MAX_BACKOFF_MS);
        }
        if (errorCount >= 7) {
            totalBackoff = Math.min(Duration.ofMinutes(30).toMillis(), MAX_BACKOFF_MS);
        }
        
        return Math.min(totalBackoff, MAX_BACKOFF_MS);
    }

    private boolean is429Error(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        return message.contains("429") || message.contains("Too Many Requests");
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        return message.contains("Connection reset") ||
               message.contains("Connection refused") ||
               message.contains("Read timed out") ||
               message.contains("connect timed out") ||
               message.contains("SocketException");
    }

    private int saveBatch(List<Concurso> concursos, TipoLoteria tipo) {
        try {
            int saved = batchService.salvarBatch(new ArrayList<>(concursos));
            log.debug("[{}] Batch de {} concursos salvo", tipo.getNome(), saved);
            return saved;
        } catch (Exception e) {
            log.error("[{}] Erro ao salvar batch: {}", tipo.getNome(), e.getMessage());
            return 0;
        }
    }

    private void pauseEntreRequisicoes() {
        long delay = MIN_DELAY_BETWEEN_REQUESTS_MS;
        
        int errors = consecutive429Errors.get();
        if (errors > 0) {
            delay = Math.min(BASE_DELAY_MS * (errors + 1), Duration.ofSeconds(5).toMillis());
        }
        
        sleep(delay);
    }

    private void pauseEntreLoterias() {
        sleep(Duration.ofSeconds(3).toMillis());
    }

    private void sleep(long ms) {
        try {
            if (ms > 60000) {
                log.info("Aguardando {} antes de continuar...", formatDuration(ms));
            }
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncAbortedException("Sincronização interrompida");
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + "m" + (seconds > 0 ? seconds + "s" : "");
    }

    private void logResumoFinal(Map<TipoLoteria, SyncResult> resultados) {
        int totalNovos = resultados.values().stream().mapToInt(SyncResult::novos).sum();
        int totalAtualizados = resultados.values().stream().mapToInt(SyncResult::atualizados).sum();
        int totalErros = resultados.values().stream().mapToInt(SyncResult::erros).sum();
        long errosLoteria = resultados.values().stream().filter(r -> r.erro() != null).count();
        
        log.info("=== RESUMO SINCRONIZAÇÃO COMPLETA ===");
        log.info("Total novos: {}", totalNovos);
        log.info("Total atualizados: {}", totalAtualizados);
        log.info("Total erros request: {}", totalErros);
        log.info("Loterias com erro: {}", errosLoteria);
        
        for (var entry : resultados.entrySet()) {
            SyncResult r = entry.getValue();
            if (r.erro() != null) {
                log.info("  {} - ERRO: {}", entry.getKey().getNome(), r.erro());
            } else {
                log.info("  {} - {} novos, {} atualizados, {} erros, {}ms", 
                        entry.getKey().getNome(), r.novos(), r.atualizados(), r.erros(), r.tempoMs());
            }
        }
    }

    public Optional<SyncProgress> getProgress(String taskId) {
        return Optional.ofNullable(progressMap.get(taskId));
    }

    public Map<String, SyncProgress> getAllProgress() {
        return new HashMap<>(progressMap);
    }

    public record SyncResult(
            String loteria,
            int novos,
            int atualizados,
            int erros,
            int semAlteracao,
            long tempoMs,
            String erro
    ) {
        public static SyncResult error(String loteria, String erro) {
            return new SyncResult(loteria, 0, 0, 0, 0, 0, erro);
        }
        
        public int sincronizados() {
            return novos + atualizados;
        }
    }

    public static class SyncProgress {
        private final String loteria;
        private final int total;
        private final AtomicInteger processed = new AtomicInteger(0);
        private final AtomicInteger errors = new AtomicInteger(0);
        private final Instant startTime = Instant.now();

        public SyncProgress(String loteria, int total) {
            this.loteria = loteria;
            this.total = total;
        }

        public void incrementProcessed() { processed.incrementAndGet(); }
        public void incrementErrors() { errors.incrementAndGet(); }

        public String getLoteria() { return loteria; }
        public int getTotal() { return total; }
        public int getProcessed() { return processed.get(); }
        public int getErrors() { return errors.get(); }
        public int getPercentComplete() { return total > 0 ? (processed.get() * 100) / total : 0; }
        public long getElapsedMs() { return Duration.between(startTime, Instant.now()).toMillis(); }
    }

    private static class SyncAbortedException extends RuntimeException {
        public SyncAbortedException(String message) {
            super(message);
        }
    }
}
