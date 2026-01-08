package br.com.loterias.controller;

import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.ApiSyncService;
import br.com.loterias.service.ApiSyncService.SyncResult;
import br.com.loterias.service.ConcursoBatchService;
import br.com.loterias.service.ExcelImportService;
import br.com.loterias.service.ExcelImportService.ImportResult;
import br.com.loterias.service.ExcelImportService.UpdateResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@Tag(name = "Importação", description = "Importação de dados de concursos via Excel e API")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ExcelImportService excelImportService;
    private final ConcursoBatchService concursoBatchService;
    private final ApiSyncService apiSyncService;

    public ImportController(ExcelImportService excelImportService, ConcursoBatchService concursoBatchService, 
                            ApiSyncService apiSyncService) {
        this.excelImportService = excelImportService;
        this.concursoBatchService = concursoBatchService;
        this.apiSyncService = apiSyncService;
    }

    @PostMapping("/{tipo}/excel")
    @Operation(summary = "Importar Excel via upload", description = "Importa resultados de concursos a partir de um arquivo Excel enviado")
    public Mono<ResponseEntity<Map<String, Object>>> importarExcel(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @RequestPart("file") FilePart file) {

        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);

        String filename = file.filename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("erro", "Apenas arquivos .xlsx são permitidos")));
        }
        // Sanitize filename: reject path traversal and unsafe characters
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\") || filename.length() > 255) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("erro", "Nome de arquivo inválido")));
        }

        log.info("Recebendo upload de Excel para {}: {}", tipoLoteria.getNome(), filename);

        final long maxUploadSize = 50L * 1024 * 1024; // 50MB
        return file.content()
                .reduce(new ByteArrayOutputStream(), (baos, dataBuffer) -> {
                    if (baos.size() + dataBuffer.readableByteCount() > maxUploadSize) {
                        throw new IllegalStateException("Arquivo excede o tamanho máximo de 50MB");
                    }
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    baos.writeBytes(bytes);
                    return baos;
                })
                .flatMap(baos -> Mono.fromCallable(() -> 
                        excelImportService.importarDoExcel(tipoLoteria, new ByteArrayInputStream(baos.toByteArray())))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(importados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("arquivo", file.filename());
                    response.put("importados", importados);
                    response.put("mensagem", String.format("%d concursos importados com sucesso", importados));
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Erro ao importar Excel de {}: {}", tipoLoteria.getNome(), e.getMessage(), e);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("erro", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(response));
                });
    }

    @PostMapping("/download-excel")
    @Operation(summary = "Baixar e importar todos", description = "Baixa os arquivos Excel oficiais da Caixa e importa todos os concursos de todas as loterias")
    public Mono<ResponseEntity<Map<String, Object>>> baixarEImportarTodosAsync() {
        log.info("Iniciando download e importação paralela de todas as loterias");

        return Mono.fromFuture(excelImportService.baixarEImportarTodosAsync())
                .map(resultados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    Map<String, Object> detalhes = new LinkedHashMap<>();
                    int totalImportados = 0;
                    int totalIgnorados = 0;
                    int erros = 0;
                    long tempoMax = 0;

                    for (Map.Entry<TipoLoteria, ImportResult> entry : resultados.entrySet()) {
                        ImportResult result = entry.getValue();
                        Map<String, Object> info = new LinkedHashMap<>();

                        if (result.importados() >= 0) {
                            info.put("importados", result.importados());
                            info.put("ignorados", result.ignorados());
                            info.put("tempoMs", result.tempoMs());
                            totalImportados += result.importados();
                            totalIgnorados += result.ignorados();
                            tempoMax = Math.max(tempoMax, result.tempoMs());
                        } else {
                            info.put("status", "ERRO");
                            erros++;
                        }
                        detalhes.put(entry.getKey().getNome(), info);
                    }

                    response.put("resultados", detalhes);
                    response.put("totalImportados", totalImportados);
                    response.put("totalIgnorados", totalIgnorados);
                    response.put("erros", erros);
                    response.put("tempoMs", tempoMax);

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Erro ao baixar/importar todos os Excels: {}", e.getMessage(), e);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("erro", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(response));
                });
    }

    @PostMapping("/{tipo}/local-excel")
    @Operation(summary = "Importar Excel local", description = "Importa resultados de um arquivo Excel já salvo localmente no servidor")
    public Mono<ResponseEntity<Map<String, Object>>> importarExcelLocal(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Importando Excel local de {}", tipoLoteria.getNome());

        if (!excelImportService.existeExcelLocal(tipoLoteria)) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("erro", "Arquivo Excel não encontrado em: " + excelImportService.getExcelPath(tipoLoteria));
            return Mono.just(ResponseEntity.badRequest().body(response));
        }

        return Mono.fromCallable(() -> excelImportService.importarDoExcelLocal(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("arquivo", excelImportService.getExcelPath(tipoLoteria).toString());
                    response.put("importados", result.importados());
                    response.put("ignorados", result.ignorados());
                    response.put("tempoMs", result.tempoMs());
                    response.put("mensagem", String.format("%d concursos importados do arquivo local em %dms",
                            result.importados(), result.tempoMs()));
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Erro ao importar Excel local de {}: {}", tipoLoteria.getNome(), e.getMessage(), e);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("erro", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(response));
                });
    }

    @GetMapping("/status")
    @Operation(summary = "Status dos arquivos Excel", description = "Retorna o status de existência dos arquivos Excel locais para cada loteria")
    public Mono<ResponseEntity<Map<String, Object>>> statusExcels() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new LinkedHashMap<>();
            Map<String, Object> arquivos = new LinkedHashMap<>();

            for (TipoLoteria tipo : TipoLoteria.values()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("existe", excelImportService.existeExcelLocal(tipo));
                info.put("caminho", excelImportService.getExcelPath(tipo).toString());
                arquivos.put(tipo.getNome(), info);
            }

            response.put("arquivos", arquivos);
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{tipo}/limpar")
    @Operation(summary = "Limpar concursos", description = "Remove todos os concursos de uma loteria para permitir reimportação")
    public Mono<ResponseEntity<Map<String, Object>>> limparConcursos(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Limpando concursos de {}", tipoLoteria.getNome());

        return Mono.fromCallable(() -> {
            int deleted = concursoBatchService.deleteByTipoLoteria(tipoLoteria);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tipo", tipoLoteria.getNome());
            response.put("removidos", deleted);
            response.put("mensagem", String.format("%d concursos removidos", deleted));
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{tipo}/atualizar-campos")
    @Operation(summary = "Atualizar campos faltantes", description = "Baixa o Excel e atualiza campos nulos dos concursos existentes (sem sobrescrever)")
    public Mono<ResponseEntity<Map<String, Object>>> atualizarCamposFaltantes(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Atualizando campos faltantes de {}", tipoLoteria.getNome());

        return Mono.fromCallable(() -> excelImportService.baixarEAtualizarCamposFaltantes(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("atualizados", result.atualizados());
                    response.put("novos", result.novos());
                    response.put("semAlteracao", result.semAlteracao());
                    response.put("tempoMs", result.tempoMs());
                    response.put("mensagem", String.format("%d concursos atualizados, %d novos inseridos em %dms",
                            result.atualizados(), result.novos(), result.tempoMs()));
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Erro ao atualizar campos de {}: {}", tipoLoteria.getNome(), e.getMessage(), e);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("erro", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(response));
                });
    }

    @PostMapping("/atualizar-todos")
    @Operation(summary = "Atualizar campos de todas as loterias", description = "Baixa todos os Excels e atualiza campos faltantes de todos os concursos")
    public Mono<ResponseEntity<Map<String, Object>>> atualizarTodosCamposFaltantes() {
        log.info("Iniciando atualização de campos faltantes de todas as loterias");

        return Mono.fromCallable(() -> excelImportService.baixarEAtualizarTodos())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    Map<String, Object> detalhes = new LinkedHashMap<>();
                    int totalAtualizados = 0;
                    int totalNovos = 0;
                    int erros = 0;

                    for (Map.Entry<TipoLoteria, UpdateResult> entry : resultados.entrySet()) {
                        UpdateResult result = entry.getValue();
                        Map<String, Object> info = new LinkedHashMap<>();

                        if (result.atualizados() >= 0) {
                            info.put("atualizados", result.atualizados());
                            info.put("novos", result.novos());
                            info.put("semAlteracao", result.semAlteracao());
                            info.put("tempoMs", result.tempoMs());
                            totalAtualizados += result.atualizados();
                            totalNovos += result.novos();
                        } else {
                            info.put("status", "ERRO");
                            erros++;
                        }
                        detalhes.put(entry.getKey().getNome(), info);
                    }

                    response.put("resultados", detalhes);
                    response.put("totalAtualizados", totalAtualizados);
                    response.put("totalNovos", totalNovos);
                    response.put("erros", erros);

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Erro ao atualizar todos os campos: {}", e.getMessage(), e);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("erro", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(response));
                });
    }

    @PostMapping("/sync-api")
    @Operation(summary = "Sincronizar todas via API (async)", 
               description = "Inicia sincronização de TODOS os concursos de todas as loterias via API da Caixa. " +
                            "Retorna imediatamente com um taskId para acompanhar o progresso.")
    public Mono<ResponseEntity<Map<String, Object>>> sincronizarTodosViaApi() {
        log.info("Iniciando sincronização completa de todas as loterias via API (async)");

        String taskId = apiSyncService.iniciarSincronizacaoTodosAsync();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "INICIADO");
        response.put("mensagem", "Sincronização iniciada em background. Use GET /api/import/sync-api/status/{taskId} para acompanhar.");
        
        return Mono.just(ResponseEntity.accepted().body(response));
    }

    @PostMapping("/{tipo}/sync-api")
    @Operation(summary = "Sincronizar loteria via API (async)", 
               description = "Inicia sincronização de TODOS os concursos de uma loteria específica via API da Caixa. " +
                            "Retorna imediatamente com um taskId para acompanhar o progresso.")
    public Mono<ResponseEntity<Map<String, Object>>> sincronizarLoteriaViaApi(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) 
            @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Iniciando sincronização via API de {} (async)", tipoLoteria.getNome());

        String taskId = apiSyncService.iniciarSincronizacaoLoteriaAsync(tipoLoteria);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("tipo", tipoLoteria.getNome());
        response.put("status", "INICIADO");
        response.put("mensagem", "Sincronização iniciada em background. Use GET /api/import/sync-api/status/{taskId} para acompanhar.");
        
        return Mono.just(ResponseEntity.accepted().body(response));
    }

    @GetMapping("/sync-api/status/{taskId}")
    @Operation(summary = "Status de uma sincronização", description = "Retorna o status e progresso de uma sincronização específica")
    public Mono<ResponseEntity<Map<String, Object>>> statusSincronizacao(@PathVariable String taskId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("taskId", taskId);
            
            if (apiSyncService.isTaskRunning(taskId)) {
                response.put("status", "EM_ANDAMENTO");
                
                var progress = apiSyncService.getProgress(taskId);
                if (progress.isPresent()) {
                    var p = progress.get();
                    response.put("loteria", p.getLoteria());
                    response.put("total", p.getTotal());
                    response.put("processados", p.getProcessed());
                    response.put("erros", p.getErrors());
                    response.put("percentual", p.getPercentComplete());
                    response.put("tempoDecorridoMs", p.getElapsedMs());
                }
            } else {
                var allResult = apiSyncService.getTaskResult(taskId);
                if (allResult.isPresent()) {
                    response.put("status", "CONCLUIDO");
                    Map<String, Object> detalhes = new LinkedHashMap<>();
                    int totalNovos = 0;
                    int totalAtualizados = 0;
                    int totalErros = 0;
                    
                    for (var entry : allResult.get().entrySet()) {
                        SyncResult r = entry.getValue();
                        Map<String, Object> info = new LinkedHashMap<>();
                        if (r.erro() == null) {
                            info.put("novos", r.novos());
                            info.put("atualizados", r.atualizados());
                            info.put("semAlteracao", r.semAlteracao());
                            info.put("erros", r.erros());
                            info.put("tempoMs", r.tempoMs());
                            totalNovos += r.novos();
                            totalAtualizados += r.atualizados();
                            totalErros += r.erros();
                        } else {
                            info.put("status", "ERRO");
                            info.put("mensagem", r.erro());
                        }
                        detalhes.put(entry.getKey().getNome(), info);
                    }
                    response.put("resultados", detalhes);
                    response.put("totalNovos", totalNovos);
                    response.put("totalAtualizados", totalAtualizados);
                    response.put("totalErros", totalErros);
                } else {
                    var singleResult = apiSyncService.getSingleTaskResult(taskId);
                    if (singleResult.isPresent()) {
                        response.put("status", "CONCLUIDO");
                        SyncResult r = singleResult.get();
                        response.put("loteria", r.loteria());
                        response.put("novos", r.novos());
                        response.put("atualizados", r.atualizados());
                        response.put("semAlteracao", r.semAlteracao());
                        response.put("erros", r.erros());
                        response.put("tempoMs", r.tempoMs());
                        if (r.erro() != null) {
                            response.put("erro", r.erro());
                        }
                    } else {
                        response.put("status", "NAO_ENCONTRADO");
                        response.put("mensagem", "Task não encontrada ou já expirada");
                    }
                }
            }
            
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/fix-missing-data")
    @Operation(summary = "Corrigir dados faltantes (async)", 
               description = "Atualiza apenas concursos com dados faltantes: local do sorteio (nomeMunicipioUFSorteio) " +
                            "e time/mês de sorte (para Timemania e Dia de Sorte). Muito mais rápido que sync-api completo.")
    public Mono<ResponseEntity<Map<String, Object>>> corrigirDadosFaltantes() {
        log.info("Iniciando correção de dados faltantes (async)");

        String taskId = apiSyncService.iniciarAtualizacaoDadosFaltantesAsync();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "INICIADO");
        response.put("mensagem", "Atualização de dados faltantes iniciada. Use GET /api/import/sync-api/status/{taskId} para acompanhar.");
        
        return Mono.just(ResponseEntity.accepted().body(response));
    }

    @PostMapping("/fix-wrong-data")
    @Operation(summary = "Corrigir dados ERRADOS (async)", 
               description = "LIMPA e re-atualiza TODOS os concursos. Usar quando os dados existentes estão incorretos " +
                            "(ex: nomeMunicipioUFSorteio contém cidade dos ganhadores em vez do local do sorteio). " +
                            "ATENÇÃO: Este processo é demorado pois consulta todos os concursos na API.")
    public Mono<ResponseEntity<Map<String, Object>>> corrigirDadosErrados() {
        log.info("Iniciando correção de dados errados (limpar + atualizar) (async)");

        String taskId = apiSyncService.iniciarCorrecaoDadosAsync();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "INICIADO");
        response.put("aviso", "Este processo limpa os dados incorretos e consulta TODOS os concursos na API. Pode demorar.");
        response.put("mensagem", "Correção iniciada. Use GET /api/import/sync-api/status/{taskId} para acompanhar.");
        
        return Mono.just(ResponseEntity.accepted().body(response));
    }

    @PostMapping("/fix-winners-data")
    @Operation(summary = "Corrigir dados de concursos COM GANHADORES (async)", 
               description = "Atualiza APENAS concursos que tiveram ganhadores na faixa principal. " +
                            "Ideal para corrigir nomeMunicipioUFSorteio que foi preenchido com cidade dos ganhadores. " +
                            "Muito mais rápido que fix-wrong-data pois atualiza apenas ~700 concursos da Mega-Sena, por exemplo.")
    public Mono<ResponseEntity<Map<String, Object>>> corrigirDadosGanhadores() {
        log.info("Iniciando correção de concursos com ganhadores (async)");

        String taskId = apiSyncService.iniciarCorrecaoGanhadoresAsync();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "INICIADO");
        response.put("descricao", "Atualiza apenas concursos que tiveram ganhadores na faixa principal.");
        response.put("mensagem", "Correção iniciada. Use GET /api/import/sync-api/status/{taskId} para acompanhar.");
        
        return Mono.just(ResponseEntity.accepted().body(response));
    }

    @GetMapping("/sync-api/progress")
    @Operation(summary = "Progresso de todas as sincronizações", description = "Retorna o progresso de todas as sincronizações em andamento")
    public Mono<ResponseEntity<Map<String, Object>>> progressoSincronizacao() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new LinkedHashMap<>();
            var progressMap = apiSyncService.getAllProgress();

            if (progressMap.isEmpty()) {
                response.put("status", "Nenhuma sincronização em andamento");
            } else {
                Map<String, Object> emAndamento = new LinkedHashMap<>();
                for (var entry : progressMap.entrySet()) {
                    var p = entry.getValue();
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("loteria", p.getLoteria());
                    info.put("total", p.getTotal());
                    info.put("processados", p.getProcessed());
                    info.put("erros", p.getErrors());
                    info.put("percentual", p.getPercentComplete());
                    info.put("tempoDecorridoMs", p.getElapsedMs());
                    emAndamento.put(entry.getKey(), info);
                }
                response.put("sincronizacoes", emAndamento);
            }

            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
