package br.com.loterias.service;

import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private static final String DOWNLOAD_URL = "https://servicebus3.caixa.gov.br/portaldeloterias/api/resultados/download?modalidade=";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int BATCH_SIZE = 100;

    private final ConcursoRepository concursoRepository;
    private final ConcursoBatchService batchService;
    private final HttpClient httpClient;
    private final Map<String, ImportStatus> importStatusMap = new ConcurrentHashMap<>();
    private final Path excelsDir;

    public ExcelImportService(ConcursoRepository concursoRepository, ConcursoBatchService batchService,
                              @Value("${loterias.excels-dir:src/main/resources/excels}") String excelsDirPath) {
        this.concursoRepository = concursoRepository;
        this.batchService = batchService;
        this.httpClient = createHttpClient();
        this.excelsDir = Path.of(excelsDirPath);

        try {
            Files.createDirectories(excelsDir);
        } catch (IOException e) {
            log.warn("Não foi possível criar diretório de excels: {}", e.getMessage());
        }
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    public Mono<ImportResult> baixarEImportarAsync(TipoLoteria tipo) {
        return Mono.fromFuture(() -> baixarEImportarFuture(tipo));
    }

    @Async
    @Transactional
    public CompletableFuture<ImportResult> baixarEImportarFuture(TipoLoteria tipo) {
        String taskId = tipo.name() + "-" + System.currentTimeMillis();
        try {
            importStatusMap.put(taskId, new ImportStatus("DOWNLOADING", 0, 0));
            byte[] excelBytes = baixarExcel(tipo);
            
            importStatusMap.put(taskId, new ImportStatus("IMPORTING", 0, 0));
            ImportResult result = importarDoExcelComBatchInternal(tipo, new ByteArrayInputStream(excelBytes));
            
            importStatusMap.put(taskId, new ImportStatus("COMPLETED", result.importados(), result.ignorados()));
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            importStatusMap.put(taskId, new ImportStatus("ERROR: " + e.getMessage(), 0, 0));
            return CompletableFuture.failedFuture(e);
        }
    }

    private byte[] baixarExcel(TipoLoteria tipo) throws IOException, InterruptedException {
        log.info("Baixando Excel de {} da Caixa...", tipo.getNome());

        String modalidade = URLEncoder.encode(tipo.getModalidadeDownload(), StandardCharsets.UTF_8);
        String url = DOWNLOAD_URL + modalidade;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "*/*")
                .header("Origin", "https://loterias.caixa.gov.br")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Falha ao baixar Excel de " + tipo.getNome() + ": HTTP " + response.statusCode());
        }

        byte[] excelBytes = response.body();
        Path excelPath = excelsDir.resolve(tipo.getEndpoint() + ".xlsx");
        Files.write(excelPath, excelBytes);
        log.info("Excel de {} salvo em: {} ({} bytes)", tipo.getNome(), excelPath, excelBytes.length);

        return excelBytes;
    }

    @Transactional
    public ImportResult importarDoExcelComBatch(TipoLoteria tipo, InputStream excelInputStream) throws IOException {
        return importarDoExcelComBatchInternal(tipo, excelInputStream);
    }

    private ImportResult importarDoExcelComBatchInternal(TipoLoteria tipo, InputStream excelInputStream) throws IOException {
        long startTime = System.currentTimeMillis();

        log.info("[{}]   Verificando concursos existentes...", tipo.getNome());
        Set<Integer> numerosExistentes = new HashSet<>(batchService.findNumerosByTipoLoteria(tipo));
        log.info("[{}]   {} concursos já existem no banco", tipo.getNome(), numerosExistentes.size());

        int importados = 0;
        int ignorados = 0;
        List<Concurso> batch = new ArrayList<>(BATCH_SIZE);

        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException("Excel sem linha de cabeçalho para " + tipo.getNome());
            }
            Map<String, Integer> colunas = mapearColunas(headerRow);
            int totalLinhas = sheet.getLastRowNum();
            log.info("[{}]   Total de linhas no Excel: {}", tipo.getNome(), totalLinhas);

            for (int i = 1; i <= totalLinhas; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Integer numero = getIntValue(row, colunas.get("Concurso"));
                if (numero == null) continue;

                if (numerosExistentes.contains(numero)) {
                    ignorados++;
                    continue;
                }

                try {
                    numerosExistentes.add(numero);
                    Concurso concurso = parsearLinha(row, colunas, tipo);
                    batch.add(concurso);

                    if (batch.size() >= BATCH_SIZE) {
                        importados += salvarBatch(batch);
                        batch.clear();
                        int percent = (int) ((importados + ignorados) * 100.0 / totalLinhas);
                        log.info("[{}]   Progresso: {}% ({} importados)", tipo.getNome(), percent, importados);
                    }
                } catch (Exception e) {
                    log.warn("[{}]   Erro linha {}: {}", tipo.getNome(), i, e.getMessage());
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                importados += salvarBatch(batch);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new ImportResult(importados, ignorados, elapsed);
    }

    private int salvarBatch(List<Concurso> concursos) {
        return batchService.salvarBatch(concursos);
    }

    @Transactional
    public int baixarEImportar(TipoLoteria tipo) throws IOException, InterruptedException {
        byte[] excelBytes = baixarExcel(tipo);
        ImportResult result = importarDoExcelComBatch(tipo, new ByteArrayInputStream(excelBytes));
        return result.importados();
    }

    public Path getExcelPath(TipoLoteria tipo) {
        return excelsDir.resolve(tipo.getEndpoint() + ".xlsx");
    }

    public boolean existeExcelLocal(TipoLoteria tipo) {
        return Files.exists(getExcelPath(tipo));
    }

    @Transactional
    public ImportResult importarDoExcelLocal(TipoLoteria tipo) throws IOException {
        Path excelPath = getExcelPath(tipo);
        if (!Files.exists(excelPath)) {
            throw new IOException("Arquivo Excel não encontrado: " + excelPath);
        }
        log.info("Importando Excel local de {}: {}", tipo.getNome(), excelPath);
        try (InputStream is = Files.newInputStream(excelPath)) {
            return importarDoExcelComBatch(tipo, is);
        }
    }

    @Transactional
    public int importarDoExcel(TipoLoteria tipo, InputStream excelInputStream) throws IOException {
        return importarDoExcelComBatch(tipo, excelInputStream).importados();
    }
    
    /**
     * Baixa Excel e atualiza concursos existentes com campos faltantes (sem sobrescrever)
     */
    @Transactional
    public UpdateResult baixarEAtualizarCamposFaltantes(TipoLoteria tipo) throws IOException, InterruptedException {
        log.info("Baixando Excel de {} para atualização de campos faltantes...", tipo.getNome());
        byte[] excelBytes = baixarExcel(tipo);
        return atualizarCamposFaltantes(tipo, new ByteArrayInputStream(excelBytes));
    }
    
    /**
     * Atualiza concursos existentes com campos faltantes do Excel (sem sobrescrever valores existentes)
     */
    @Transactional
    public UpdateResult atualizarCamposFaltantes(TipoLoteria tipo, InputStream excelInputStream) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Iniciando atualização de campos faltantes...", tipo.getNome());
        
        int atualizados = 0;
        int semAlteracao = 0;
        int novos = 0;
        List<Concurso> batchUpdate = new ArrayList<>(BATCH_SIZE);
        List<Concurso> batchInsert = new ArrayList<>(BATCH_SIZE);
        
        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException("Excel sem linha de cabeçalho para " + tipo.getNome());
            }
            Map<String, Integer> colunas = mapearColunas(headerRow);
            int totalLinhas = sheet.getLastRowNum();
            log.info("[{}] Total de linhas no Excel: {}", tipo.getNome(), totalLinhas);
            
            for (int i = 1; i <= totalLinhas; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Integer numero = getIntValue(row, colunas.get("Concurso"));
                if (numero == null) continue;
                
                Optional<Concurso> existenteOpt = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
                
                if (existenteOpt.isPresent()) {
                    Concurso existente = existenteOpt.get();
                    Concurso dadosExcel = parsearLinha(row, colunas, tipo);
                    boolean atualizado = atualizarCamposNulos(existente, dadosExcel);
                    
                    if (atualizado) {
                        batchUpdate.add(existente);
                        if (batchUpdate.size() >= BATCH_SIZE) {
                            atualizados += salvarBatchUpdate(batchUpdate);
                            batchUpdate.clear();
                            log.info("[{}] Progresso: {} atualizados", tipo.getNome(), atualizados);
                        }
                    } else {
                        semAlteracao++;
                    }
                } else {
                    Concurso novo = parsearLinha(row, colunas, tipo);
                    batchInsert.add(novo);
                    if (batchInsert.size() >= BATCH_SIZE) {
                        novos += salvarBatch(batchInsert);
                        batchInsert.clear();
                    }
                }
            }
            
            if (!batchUpdate.isEmpty()) {
                atualizados += salvarBatchUpdate(batchUpdate);
            }
            if (!batchInsert.isEmpty()) {
                novos += salvarBatch(batchInsert);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[{}] Atualização concluída: {} atualizados, {} novos, {} sem alteração em {}ms", 
                tipo.getNome(), atualizados, novos, semAlteracao, elapsed);
        return new UpdateResult(atualizados, novos, semAlteracao, elapsed);
    }
    
    private int salvarBatchUpdate(List<Concurso> concursos) {
        concursoRepository.saveAll(concursos);
        return concursos.size();
    }
    
    /**
     * Atualiza campos nulos do existente com valores do dadosExcel
     * @return true se houve alguma alteração
     */
    private boolean atualizarCamposNulos(Concurso existente, Concurso dadosExcel) {
        boolean atualizado = false;
        
        if (existente.getValorArrecadado() == null && dadosExcel.getValorArrecadado() != null) {
            existente.setValorArrecadado(dadosExcel.getValorArrecadado());
            atualizado = true;
        }
        if (existente.getValorEstimadoProximoConcurso() == null && dadosExcel.getValorEstimadoProximoConcurso() != null) {
            existente.setValorEstimadoProximoConcurso(dadosExcel.getValorEstimadoProximoConcurso());
            atualizado = true;
        }
        if (existente.getValorAcumuladoProximoConcurso() == null && dadosExcel.getValorAcumuladoProximoConcurso() != null) {
            existente.setValorAcumuladoProximoConcurso(dadosExcel.getValorAcumuladoProximoConcurso());
            atualizado = true;
        }
        if (existente.getValorAcumuladoConcursoEspecial() == null && dadosExcel.getValorAcumuladoConcursoEspecial() != null) {
            existente.setValorAcumuladoConcursoEspecial(dadosExcel.getValorAcumuladoConcursoEspecial());
            atualizado = true;
        }
        if (existente.getValorTotalPremioFaixaUm() == null && dadosExcel.getValorTotalPremioFaixaUm() != null) {
            existente.setValorTotalPremioFaixaUm(dadosExcel.getValorTotalPremioFaixaUm());
            atualizado = true;
        }
        // NOTA: Não atualizar nomeMunicipioUFSorteio do Excel, pois essa coluna contém
        // a cidade dos GANHADORES, não o local do sorteio. Use apenas dados da API JSON.
        if (existente.getNomeTimeCoracaoMesSorte() == null && dadosExcel.getNomeTimeCoracaoMesSorte() != null) {
            existente.setNomeTimeCoracaoMesSorte(dadosExcel.getNomeTimeCoracaoMesSorte());
            atualizado = true;
        }
        if (existente.getObservacao() == null && dadosExcel.getObservacao() != null) {
            existente.setObservacao(dadosExcel.getObservacao());
            atualizado = true;
        }
        if (existente.getAcumulado() == null && dadosExcel.getAcumulado() != null) {
            existente.setAcumulado(dadosExcel.getAcumulado());
            atualizado = true;
        }
        if ((existente.getDezenasSegundoSorteio() == null || existente.getDezenasSegundoSorteio().isEmpty()) 
                && dadosExcel.getDezenasSegundoSorteio() != null && !dadosExcel.getDezenasSegundoSorteio().isEmpty()) {
            existente.setDezenasSegundoSorteio(dadosExcel.getDezenasSegundoSorteio());
            atualizado = true;
        }
        
        // Atualizar faixas de premiação se não existem
        if (existente.getFaixasPremiacao().isEmpty() && !dadosExcel.getFaixasPremiacao().isEmpty()) {
            for (FaixaPremiacao faixa : dadosExcel.getFaixasPremiacao()) {
                existente.addFaixaPremiacao(faixa);
            }
            atualizado = true;
        }
        
        return atualizado;
    }
    
    /**
     * Baixa e atualiza campos faltantes de todas as loterias usando virtual threads
     */
    public Map<TipoLoteria, UpdateResult> baixarEAtualizarTodos() {
        log.info("========================================");
        log.info("INICIANDO ATUALIZAÇÃO DE TODAS AS LOTERIAS (Virtual Threads)");
        log.info("========================================");
        long startTime = System.currentTimeMillis();
        
        Map<TipoLoteria, UpdateResult> resultados = new ConcurrentHashMap<>();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = Arrays.stream(TipoLoteria.values())
                    .map(tipo -> CompletableFuture.runAsync(() -> {
                        try {
                            log.info("[{}] ▶ Iniciando atualização...", tipo.getNome());
                            UpdateResult result = baixarEAtualizarCamposFaltantes(tipo);
                            resultados.put(tipo, result);
                            log.info("[{}] ✓ Concluído: {} atualizados, {} novos em {}ms", 
                                    tipo.getNome(), result.atualizados(), result.novos(), result.tempoMs());
                        } catch (Exception e) {
                            log.error("[{}] ✗ Erro: {}", tipo.getNome(), e.getMessage());
                            resultados.put(tipo, new UpdateResult(-1, 0, 0, 0));
                        }
                    }, executor))
                    .toList();
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        int totalAtualizados = resultados.values().stream().mapToInt(r -> Math.max(0, r.atualizados())).sum();
        int totalNovos = resultados.values().stream().mapToInt(UpdateResult::novos).sum();
        
        log.info("========================================");
        log.info("ATUALIZAÇÃO CONCLUÍDA");
        log.info("Total atualizados: {}", totalAtualizados);
        log.info("Total novos: {}", totalNovos);
        log.info("Tempo total: {}s", elapsed / 1000);
        log.info("========================================");
        
        return resultados;
    }

    public Map<TipoLoteria, Integer> baixarEImportarTodos() {
        Map<TipoLoteria, Integer> resultados = new ConcurrentHashMap<>();

        for (TipoLoteria tipo : TipoLoteria.values()) {
            try {
                int importados = baixarEImportar(tipo);
                resultados.put(tipo, importados);
            } catch (Exception e) {
                log.error("Erro ao importar {}: {}", tipo.getNome(), e.getMessage());
                resultados.put(tipo, -1);
            }
        }

        return resultados;
    }

    @Async
    public CompletableFuture<Map<TipoLoteria, ImportResult>> baixarEImportarTodosAsync() {
        log.info("========================================");
        log.info("INICIANDO IMPORTAÇÃO DE TODAS AS LOTERIAS");
        log.info("========================================");
        long startTime = System.currentTimeMillis();
        
        Map<TipoLoteria, ImportResult> resultados = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        TipoLoteria[] tipos = TipoLoteria.values();
        log.info("Total de loterias a processar: {}", tipos.length);
        
        List<CompletableFuture<Void>> futures = Arrays.stream(tipos)
                .map(tipo -> CompletableFuture.runAsync(() -> {
                    long tipoStart = System.currentTimeMillis();
                    try {
                        log.info("[{}] ▶ Iniciando download...", tipo.getNome());
                        byte[] excelBytes = baixarExcel(tipo);
                        log.info("[{}] ✓ Download concluído ({} KB)", tipo.getNome(), excelBytes.length / 1024);
                        
                        log.info("[{}] ▶ Iniciando importação...", tipo.getNome());
                        ImportResult result = importarDoExcelComBatchThreadSafe(tipo, new ByteArrayInputStream(excelBytes));
                        resultados.put(tipo, result);
                        
                        long tipoElapsed = System.currentTimeMillis() - tipoStart;
                        log.info("[{}] ✓ CONCLUÍDO: {} importados, {} ignorados em {}s", 
                                tipo.getNome(), result.importados(), result.ignorados(), tipoElapsed / 1000);
                    } catch (Exception e) {
                        log.error("[{}] ✗ ERRO: {}", tipo.getNome(), e.getMessage());
                        resultados.put(tipo, new ImportResult(-1, 0, 0));
                    }
                }, executor))
                .toList();
        
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        int totalImportados = resultados.values().stream().mapToInt(r -> Math.max(0, r.importados())).sum();
        int totalIgnorados = resultados.values().stream().mapToInt(ImportResult::ignorados).sum();
        
        log.info("========================================");
        log.info("IMPORTAÇÃO CONCLUÍDA");
        log.info("Total importados: {}", totalImportados);
        log.info("Total ignorados: {}", totalIgnorados);
        log.info("Tempo total: {}s", elapsed / 1000);
        log.info("========================================");
        
        return CompletableFuture.completedFuture(resultados);
    }

    @Transactional
    public ImportResult importarDoExcelComBatchThreadSafe(TipoLoteria tipo, InputStream excelInputStream) throws IOException {
        return importarDoExcelComBatchInternal(tipo, excelInputStream);
    }

    private static final long STATUS_TTL_MS = 3_600_000; // 1 hour

    public ImportStatus getImportStatus(String taskId) {
        // Lazy cleanup of expired entries
        long now = System.currentTimeMillis();
        importStatusMap.entrySet().removeIf(e -> now - e.getValue().createdAt() > STATUS_TTL_MS);
        return importStatusMap.get(taskId);
    }

    private Map<String, Integer> mapearColunas(Row headerRow) {
        Map<String, Integer> colunas = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String nome = cell.getStringCellValue().trim();
                colunas.put(nome, i);
            }
        }
        log.debug("Colunas encontradas: {}", colunas.keySet());
        return colunas;
    }

    private Integer getDuplaSenaBola(Row row, Map<String, Integer> colunas, int bola, int sorteio) {
        String[] possiveisNomes = {
            "Bola" + bola + " sorteio " + sorteio,
            "Bola" + bola + " Sorteio " + sorteio,
            "bola" + bola + " sorteio " + sorteio
        };
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                return getIntValue(row, colunas.get(nome));
            }
        }
        for (String key : colunas.keySet()) {
            if (key.toLowerCase().contains("bola" + bola) && 
                key.toLowerCase().contains("sorteio " + sorteio)) {
                return getIntValue(row, colunas.get(key));
            }
        }
        return null;
    }

    private Integer getSuperSeteBola(Row row, Map<String, Integer> colunas, int coluna) {
        String[] possiveisNomes = {
            "Coluna " + coluna,
            "coluna " + coluna,
            "Coluna" + coluna
        };
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                return getIntValue(row, colunas.get(nome));
            }
        }
        for (String key : colunas.keySet()) {
            if (key.toLowerCase().contains("coluna") && key.contains(String.valueOf(coluna))) {
                return getIntValue(row, colunas.get(key));
            }
        }
        return null;
    }

    private Integer getTrevo(Row row, Map<String, Integer> colunas, int trevo) {
        String[] possiveisNomes = {
            "Trevo" + trevo,
            "Trevo " + trevo,
            "trevo" + trevo
        };
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                return getIntValue(row, colunas.get(nome));
            }
        }
        for (String key : colunas.keySet()) {
            if (key.toLowerCase().contains("trevo") && key.contains(String.valueOf(trevo))) {
                return getIntValue(row, colunas.get(key));
            }
        }
        return null;
    }

    private Concurso parsearLinha(Row row, Map<String, Integer> colunas, TipoLoteria tipo) {
        Concurso concurso = new Concurso();
        concurso.setTipoLoteria(tipo);
        concurso.setNumero(getIntValue(row, colunas.get("Concurso")));

        String dataColuna = colunas.containsKey("Data do Sorteio") ? "Data do Sorteio" : "Data Sorteio";
        concurso.setDataApuracao(getDateValue(row, colunas.get(dataColuna)));
        
        // Campos comuns a todas loterias
        concurso.setValorArrecadado(getValorArrecadado(row, colunas));
        concurso.setValorEstimadoProximoConcurso(getEstimativaPremio(row, colunas));
        concurso.setObservacao(getObservacao(row, colunas));
        
        // Acumulado específico da loteria
        BigDecimal acumulado = getAcumuladoPrincipal(row, colunas, tipo);
        concurso.setValorAcumuladoProximoConcurso(acumulado);
        concurso.setAcumulado(acumulado != null && acumulado.compareTo(BigDecimal.ZERO) > 0);
        
        // Acumulado de concursos especiais
        concurso.setValorAcumuladoConcursoEspecial(getAcumuladoEspecial(row, colunas, tipo));
        
        // NOTA: A coluna "Cidade / UF" nos excels da Caixa contém a cidade dos GANHADORES,
        // não o local do sorteio. O campo nomeMunicipioUFSorteio deve vir apenas da API JSON.
        // Não mapeamos getCidadeUF para nomeMunicipioUFSorteio para evitar inconsistências.
        
        // Time do Coração (Timemania) ou Mês de Sorte (Dia de Sorte)
        concurso.setNomeTimeCoracaoMesSorte(getTimeCoracaoMesSorte(row, colunas, tipo));

        List<Integer> dezenas = new ArrayList<>();
        if (tipo == TipoLoteria.DUPLA_SENA) {
            for (int i = 1; i <= 6; i++) {
                Integer dezena = getDuplaSenaBola(row, colunas, i, 1);
                if (dezena != null) {
                    dezenas.add(dezena);
                }
            }
            for (int i = 1; i <= 6; i++) {
                Integer dezena = getDuplaSenaBola(row, colunas, i, 2);
                if (dezena != null) {
                    dezenas.add(dezena);
                }
            }
        } else if (tipo == TipoLoteria.SUPER_SETE) {
            for (int i = 1; i <= 7; i++) {
                Integer dezena = getSuperSeteBola(row, colunas, i);
                if (dezena != null) {
                    dezenas.add(dezena);
                }
            }
        } else if (tipo == TipoLoteria.MAIS_MILIONARIA) {
            for (int i = 1; i <= 6; i++) {
                Integer dezena = getIntValue(row, colunas.get("Bola" + i));
                if (dezena != null) {
                    dezenas.add(dezena);
                }
            }
            for (int i = 1; i <= 2; i++) {
                Integer trevo = getTrevo(row, colunas, i);
                if (trevo != null) {
                    dezenas.add(trevo);
                }
            }
        } else {
            for (int i = 1; i <= tipo.getQuantidadeBolas(); i++) {
                Integer dezena = getIntValue(row, colunas.get("Bola" + i));
                if (dezena != null) {
                    dezenas.add(dezena);
                }
            }
        }
        concurso.setDezenasSorteadas(dezenas);
        
        // Segundo sorteio para Dupla Sena (já está em dezenas, mas também separado)
        if (tipo == TipoLoteria.DUPLA_SENA) {
            List<Integer> segundoSorteio = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                Integer dezena = getDuplaSenaBola(row, colunas, i, 2);
                if (dezena != null) {
                    segundoSorteio.add(dezena);
                }
            }
            concurso.setDezenasSegundoSorteio(segundoSorteio);
        }

        List<FaixaPremiacao> faixas = parsearFaixasPremiacao(row, colunas, tipo);
        for (FaixaPremiacao faixa : faixas) {
            concurso.addFaixaPremiacao(faixa);
        }
        
        // Calcular valor total da faixa principal
        if (!faixas.isEmpty()) {
            FaixaPremiacao faixaPrincipal = faixas.getFirst();
            if (faixaPrincipal.getNumeroGanhadores() > 0 && faixaPrincipal.getValorPremio() != null) {
                BigDecimal total = faixaPrincipal.getValorPremio()
                        .multiply(BigDecimal.valueOf(faixaPrincipal.getNumeroGanhadores()));
                concurso.setValorTotalPremioFaixaUm(total);
            }
        }

        return concurso;
    }
    
    private BigDecimal getValorArrecadado(Row row, Map<String, Integer> colunas) {
        String[] possiveisNomes = {"Arrecadacao Total", "Arrecadação Total", "Arrecadacao total", "arrecadacao total"};
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                BigDecimal valor = getBigDecimalValue(row, colunas.get(nome));
                if (valor != null) return valor;
            }
        }
        return null;
    }
    
    private BigDecimal getEstimativaPremio(Row row, Map<String, Integer> colunas) {
        String[] possiveisNomes = {"Estimativa Prêmio", "Estimativa prêmio", "Estimativa Premio", "estimativa premio"};
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                BigDecimal valor = getBigDecimalValue(row, colunas.get(nome));
                if (valor != null) return valor;
            }
        }
        return null;
    }
    
    private String getObservacao(Row row, Map<String, Integer> colunas) {
        String[] possiveisNomes = {"Observação", "observação", "Observacao", "observacao"};
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                String valor = getStringValue(row, colunas.get(nome));
                if (valor != null && !valor.isBlank()) return valor;
            }
        }
        return null;
    }

    private String getTimeCoracaoMesSorte(Row row, Map<String, Integer> colunas, TipoLoteria tipo) {
        if (tipo != TipoLoteria.TIMEMANIA && tipo != TipoLoteria.DIA_DE_SORTE) {
            return null;
        }
        
        String[] possiveisNomes = {"Time do Coração", "Time Coração", "Mês da Sorte", "Mês de Sorte", "Mes da Sorte"};
        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                String valor = getStringValue(row, colunas.get(nome));
                if (valor != null && !valor.isBlank()) {
                    return valor.trim();
                }
            }
        }
        return null;
    }

    private BigDecimal getAcumuladoPrincipal(Row row, Map<String, Integer> colunas, TipoLoteria tipo) {
        String[] possiveisNomes = {
                "Acumulado 6 acertos",
                "Acumulado 6 acertos + 2 Trevos",
                "Acumulado\n15 acertos",
                "Acumulado 15 acertos",
                "Acumulado 5 acertos",
                "Acumulado 20 acertos",
                "Acumulado 10 acertos",
                "Acumulado 7 acertos",
                "Acumulado 6 acertos sorteio 1"
        };

        for (String nome : possiveisNomes) {
            if (colunas.containsKey(nome)) {
                BigDecimal valor = getBigDecimalValue(row, colunas.get(nome));
                if (valor != null) return valor;
            }
        }
        return null;
    }
    
    private BigDecimal getAcumuladoEspecial(Row row, Map<String, Integer> colunas, TipoLoteria tipo) {
        Map<TipoLoteria, String[]> nomesPorTipo = Map.of(
                TipoLoteria.MEGA_SENA, new String[]{"Acumulado Sorteio Especial Mega da Virada"},
                TipoLoteria.LOTOFACIL, new String[]{"Acumulado sorteio especial Lotofácil da Independência", "Acumulado Sorteio Especial Lotofácil da Independência"},
                TipoLoteria.QUINA, new String[]{"Acumulado Sorteio Especial Quina de São João"},
                TipoLoteria.DUPLA_SENA, new String[]{"Acumulado Sorteio Especial Dupla de Páscoa"}
        );
        
        String[] nomes = nomesPorTipo.get(tipo);
        if (nomes != null) {
            for (String nome : nomes) {
                if (colunas.containsKey(nome)) {
                    BigDecimal valor = getBigDecimalValue(row, colunas.get(nome));
                    if (valor != null) return valor;
                }
            }
        }
        return null;
    }

    private List<FaixaPremiacao> parsearFaixasPremiacao(Row row, Map<String, Integer> colunas, TipoLoteria tipo) {
        List<FaixaPremiacao> faixas = new ArrayList<>();

        Map<Integer, String[]> faixasPorTipo = getFaixasConfig(tipo);

        int faixaNum = 1;
        for (Map.Entry<Integer, String[]> entry : faixasPorTipo.entrySet()) {
            String[] config = entry.getValue();
            String ganhadoresCol = config[0];
            String rateioCol = config[1];
            String descricao = config[2];

            Integer ganhadores = getIntValue(row, colunas.get(ganhadoresCol));
            BigDecimal premio = getBigDecimalValue(row, colunas.get(rateioCol));

            if (ganhadores != null || premio != null) {
                FaixaPremiacao faixa = new FaixaPremiacao();
                faixa.setFaixa(faixaNum);
                faixa.setDescricaoFaixa(descricao);
                faixa.setNumeroGanhadores(ganhadores != null ? ganhadores : 0);
                faixa.setValorPremio(premio != null ? premio : BigDecimal.ZERO);
                faixas.add(faixa);
            }
            faixaNum++;
        }

        return faixas;
    }

    private Map<Integer, String[]> getFaixasConfig(TipoLoteria tipo) {
        Map<Integer, String[]> faixas = new LinkedHashMap<>();

        switch (tipo) {
            case MEGA_SENA -> {
                faixas.put(1, new String[]{"Ganhadores 6 acertos", "Rateio 6 acertos", "6 acertos"});
                faixas.put(2, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(3, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
            }
            case LOTOFACIL -> {
                faixas.put(1, new String[]{"Ganhadores 15 acertos", "Rateio 15 acertos", "15 acertos"});
                faixas.put(2, new String[]{"Ganhadores 14 acertos", "Rateio 14 acertos", "14 acertos"});
                faixas.put(3, new String[]{"Ganhadores 13 acertos", "Rateio 13 acertos", "13 acertos"});
                faixas.put(4, new String[]{"Ganhadores 12 acertos", "Rateio 12 acertos", "12 acertos"});
                faixas.put(5, new String[]{"Ganhadores 11 acertos", "Rateio 11 acertos", "11 acertos"});
            }
            case QUINA -> {
                faixas.put(1, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(2, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
                faixas.put(3, new String[]{"Ganhadores 3 acertos", "Rateio 3 acertos", "3 acertos"});
                faixas.put(4, new String[]{"Ganhadores 2 acertos", "Rateio 2 acertos", "2 acertos"});
            }
            case LOTOMANIA -> {
                faixas.put(1, new String[]{"Ganhadores 20 acertos", "Rateio 20 acertos", "20 acertos"});
                faixas.put(2, new String[]{"Ganhadores 19 acertos", "Rateio 19 acertos", "19 acertos"});
                faixas.put(3, new String[]{"Ganhadores 18 acertos", "Rateio 18 acertos", "18 acertos"});
                faixas.put(4, new String[]{"Ganhadores 17 acertos", "Rateio 17 acertos", "17 acertos"});
                faixas.put(5, new String[]{"Ganhadores 16 acertos", "Rateio 16 acertos", "16 acertos"});
                faixas.put(6, new String[]{"Ganhadores 15 acertos", "Rateio 15 acertos", "15 acertos"});
                faixas.put(7, new String[]{"Ganhadores 0 acertos", "Rateio 0 acertos", "0 acertos"});
            }
            case TIMEMANIA -> {
                faixas.put(1, new String[]{"Ganhadores 7 acertos", "Rateio 7 acertos", "7 acertos"});
                faixas.put(2, new String[]{"Ganhadores 6 acertos", "Rateio 6 acertos", "6 acertos"});
                faixas.put(3, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(4, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
                faixas.put(5, new String[]{"Ganhadores 3 acertos", "Rateio 3 acertos", "3 acertos"});
            }
            case DUPLA_SENA -> {
                faixas.put(1, new String[]{"Ganhadores 6 acertos", "Rateio 6 acertos", "6 acertos"});
                faixas.put(2, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(3, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
                faixas.put(4, new String[]{"Ganhadores 3 acertos", "Rateio 3 acertos", "3 acertos"});
            }
            case DIA_DE_SORTE -> {
                faixas.put(1, new String[]{"Ganhadores 7 acertos", "Rateio 7 acertos", "7 acertos"});
                faixas.put(2, new String[]{"Ganhadores 6 acertos", "Rateio 6 acertos", "6 acertos"});
                faixas.put(3, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(4, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
            }
            case SUPER_SETE -> {
                faixas.put(1, new String[]{"Ganhadores 7 acertos", "Rateio 7 acertos", "7 acertos"});
                faixas.put(2, new String[]{"Ganhadores 6 acertos", "Rateio 6 acertos", "6 acertos"});
                faixas.put(3, new String[]{"Ganhadores 5 acertos", "Rateio 5 acertos", "5 acertos"});
                faixas.put(4, new String[]{"Ganhadores 4 acertos", "Rateio 4 acertos", "4 acertos"});
                faixas.put(5, new String[]{"Ganhadores 3 acertos", "Rateio 3 acertos", "3 acertos"});
            }
            case MAIS_MILIONARIA -> {
                faixas.put(1, new String[]{"Ganhadores 6+2", "Rateio 6+2", "6 acertos + 2 trevos"});
                faixas.put(2, new String[]{"Ganhadores 6+1", "Rateio 6+1", "6 acertos + 1 trevo"});
                faixas.put(3, new String[]{"Ganhadores 6+0", "Rateio 6+0", "6 acertos + 0 trevos"});
                faixas.put(4, new String[]{"Ganhadores 5+2", "Rateio 5+2", "5 acertos + 2 trevos"});
                faixas.put(5, new String[]{"Ganhadores 5+1", "Rateio 5+1", "5 acertos + 1 trevo"});
                faixas.put(6, new String[]{"Ganhadores 5+0", "Rateio 5+0", "5 acertos + 0 trevos"});
                faixas.put(7, new String[]{"Ganhadores 4+2", "Rateio 4+2", "4 acertos + 2 trevos"});
                faixas.put(8, new String[]{"Ganhadores 4+1", "Rateio 4+1", "4 acertos + 1 trevo"});
                faixas.put(9, new String[]{"Ganhadores 4+0", "Rateio 4+0", "4 acertos + 0 trevos"});
                faixas.put(10, new String[]{"Ganhadores 3+2", "Rateio 3+2", "3 acertos + 2 trevos"});
            }
        }

        return faixas;
    }

    private Integer getIntValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> {
                    String val = cell.getStringCellValue().trim();
                    if (val.isEmpty()) yield null;
                    yield Integer.parseInt(val.replaceAll("[^0-9-]", ""));
                }
                case FORMULA -> {
                    try {
                        yield (int) cell.getNumericCellValue();
                    } catch (Exception e) {
                        yield null;
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getBigDecimalValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try {
                    String value = cell.getStringCellValue().trim()
                            .replace("R$", "")
                            .replace(".", "")
                            .replace(",", ".")
                            .trim();
                    yield new BigDecimal(value);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String getStringValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> null;
        };
    }

    private LocalDate getDateValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate();
                }
                yield null;
            }
            case STRING -> {
                try {
                    yield LocalDate.parse(cell.getStringCellValue().trim(), DATE_FORMATTER);
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    public record ImportResult(int importados, int ignorados, long tempoMs) {}
    public record ImportStatus(String status, int importados, int ignorados, long createdAt) {
        public ImportStatus(String status, int importados, int ignorados) {
            this(status, importados, ignorados, System.currentTimeMillis());
        }
    }
    public record UpdateResult(int atualizados, int novos, int semAlteracao, long tempoMs) {}
}
