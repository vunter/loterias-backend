package br.com.loterias.controller;

import br.com.loterias.controller.GlobalExceptionHandler.LoteriaNotFoundException;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.service.AtualizarGanhadoresService;
import br.com.loterias.service.ConcursoSyncService;
import br.com.loterias.service.SyncRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/concursos")
@Tag(name = "Concursos", description = "Consulta e sincronização de concursos")
public class ConcursoController {

    private static final Logger log = LoggerFactory.getLogger(ConcursoController.class);

    private final ConcursoRepository concursoRepository;
    private final ConcursoSyncService concursoSyncService;
    private final AtualizarGanhadoresService atualizarGanhadoresService;
    private final SyncRateLimitService rateLimitService;

    public ConcursoController(ConcursoRepository concursoRepository, 
                               ConcursoSyncService concursoSyncService,
                               AtualizarGanhadoresService atualizarGanhadoresService,
                               SyncRateLimitService rateLimitService) {
        this.concursoRepository = concursoRepository;
        this.concursoSyncService = concursoSyncService;
        this.atualizarGanhadoresService = atualizarGanhadoresService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/{tipo}")
    @Operation(summary = "Listar concursos paginados", description = "Retorna concursos de uma loteria com paginação ordenados do mais recente para o mais antigo")
    public Mono<Page<Concurso>> listarConcursos(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Número da página (0-indexed)", example = "0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamanho da página (máx 100)", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> concursoRepository.findByTipoLoteriaPaged(tipoLoteria, PageRequest.of(page, Math.min(size, 100))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/{numero}")
    @Operation(summary = "Buscar concurso por número", description = "Retorna um concurso específico pelo número com dezenas sorteadas e faixas de premiação")
    public Mono<Concurso> buscarConcurso(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Número do concurso", example = "2800") @PathVariable Integer numero) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> concursoRepository.findByTipoLoteriaAndNumero(tipoLoteria, numero)
                        .orElseThrow(() -> new LoteriaNotFoundException(
                                String.format("Concurso %d de %s não encontrado", numero, tipoLoteria.getNome()))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/ultimo")
    @Operation(summary = "Buscar último concurso", description = "Retorna o concurso mais recente de uma loteria com todas as informações")
    public Mono<Concurso> buscarUltimoConcurso(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> {
                    Integer maxNumero = concursoRepository.findMaxNumeroByTipoLoteria(tipoLoteria)
                            .orElseThrow(() -> new LoteriaNotFoundException(
                                    String.format("Nenhum concurso de %s encontrado", tipoLoteria.getNome())));
                    return concursoRepository.findByTipoLoteriaAndNumero(tipoLoteria, maxNumero)
                            .orElseThrow(() -> new LoteriaNotFoundException(
                                    String.format("Concurso %d de %s não encontrado", maxNumero, tipoLoteria.getNome())));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{tipo}/sync-full")
    @Operation(summary = "Sincronização completa", description = "Baixa TODOS os concursos históricos da loteria via API da Caixa (pode demorar vários minutos). Rate limited.",
            responses = @ApiResponse(responseCode = "200", description = "Resultado da sincronização",
                    content = @Content(examples = @ExampleObject(value = "{\"tipo\": \"Mega-Sena\", \"sincronizados\": 2800, \"mensagem\": \"Carga completa: 2800 concursos sincronizados\"}"))))
    public Mono<Map<String, Object>> sincronizarTodosLoteria(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        
        SyncRateLimitService.RateLimitStatus status = rateLimitService.checkRateLimit();
        if (!status.allowed()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tipo", tipoLoteria.getNome());
            response.put("sincronizados", 0);
            response.put("mensagem", "Rate limited. Aguarde " + status.remainingSeconds() + " segundos.");
            response.put("rateLimited", true);
            response.put("remainingSeconds", status.remainingSeconds());
            return Mono.just(response);
        }
        
        log.info("Sincronização completa iniciada para {}", tipoLoteria.getNome());
        rateLimitService.recordSync();
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarTodosConcursos(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(sincronizados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("sincronizados", sincronizados);
                    response.put("mensagem", String.format("Carga completa: %d concursos sincronizados", sincronizados));
                    return response;
                });
    }

    @PostMapping("/{tipo}/sync")
    @Operation(summary = "Sincronizar novos concursos", description = "Sincroniza apenas os concursos mais recentes que ainda não estão no banco",
            responses = @ApiResponse(responseCode = "200", description = "Resultado da sincronização",
                    content = @Content(examples = @ExampleObject(value = "{\"tipo\": \"Mega-Sena\", \"sincronizados\": 3, \"mensagem\": \"3 concursos sincronizados\"}"))))
    public Mono<Map<String, Object>> sincronizarLoteria(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Sincronização manual iniciada para {}", tipoLoteria.getNome());
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarNovosConcursos(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(sincronizados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("sincronizados", sincronizados);
                    response.put("mensagem", String.format("%d concursos sincronizados", sincronizados));
                    return response;
                });
    }

    @PostMapping("/sync-all")
    @Operation(summary = "Sincronizar todas as loterias", description = "Sincroniza novos concursos de TODAS as loterias de uma vez")
    public Mono<Map<String, Object>> sincronizarTodasLoterias() {
        log.info("Sincronização manual de todas as loterias iniciada");
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarTodosNovosConcursos())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalSincronizados = resultados.values().stream().mapToInt(Integer::intValue).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("resultados", resultados);
                    response.put("totalSincronizados", totalSincronizados);
                    return response;
                });
    }

    @PostMapping("/sync-ultimos")
    @Operation(summary = "Sincronizar último concurso de cada loteria", 
               description = "Sincroniza apenas o último concurso de cada loteria de forma sequencial com delay de 2s entre requisições para evitar rate limit")
    public Mono<Map<String, Object>> sincronizarUltimosConcursos() {
        log.info("Sincronização dos últimos concursos iniciada (modo seguro)");
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarUltimosConcursosTodos())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalSincronizados = resultados.values().stream()
                            .mapToInt(ConcursoSyncService.SyncResult::sincronizados).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("resultados", resultados);
                    response.put("totalSincronizados", totalSincronizados);
                    return response;
                });
    }

    @PostMapping("/sync-ultimos-dias")
    @Operation(summary = "Sincronizar últimos N dias de todas as loterias",
               description = "Atualiza (upsert) todos os concursos dos últimos N dias de todas as loterias. " +
                            "Padrão: 30 dias. Útil para refresh de dados recentes incluindo valores atualizados de prêmios e ganhadores.")
    public Mono<Map<String, Object>> sincronizarUltimosDias(
            @Parameter(description = "Quantidade de dias para sincronizar", example = "30")
            @RequestParam(defaultValue = "30") int dias) {
        log.info("Sincronização dos últimos {} dias de todas as loterias iniciada", dias);
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarTodosUltimosDias(Math.min(dias, 365)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalSincronizados = resultados.values().stream().mapToInt(Integer::intValue).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("dias", dias);
                    response.put("resultados", resultados);
                    response.put("totalSincronizados", totalSincronizados);
                    return response;
                });
    }

    @PostMapping("/{tipo}/sync-ultimo")
    @Operation(summary = "Sincronizar último concurso de uma loteria", 
               description = "Sincroniza apenas o último concurso disponível de uma loteria específica. Rate limited: 1 requisição a cada 2 minutos.")
    public Mono<Map<String, Object>> sincronizarUltimoConcurso(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) 
            @PathVariable String tipo) {
        
        // Check rate limit
        var rateLimitStatus = rateLimitService.checkRateLimit();
        if (!rateLimitStatus.allowed()) {
            log.warn("Rate limit atingido. Aguarde {} segundos.", rateLimitStatus.remainingSeconds());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tipo", tipo);
            response.put("sincronizados", 0);
            response.put("sucesso", false);
            response.put("mensagem", String.format("Rate limit atingido. Aguarde %d segundos.", rateLimitStatus.remainingSeconds()));
            response.put("rateLimited", true);
            response.put("remainingSeconds", rateLimitStatus.remainingSeconds());
            response.put("cooldownSeconds", rateLimitService.getCooldownSeconds());
            return Mono.just(response);
        }
        
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Sincronização do último concurso de {} iniciada", tipoLoteria.getNome());
        
        // Record sync attempt
        rateLimitService.recordSync();
        
        return Mono.fromCallable(() -> concursoSyncService.sincronizarUltimoConcurso(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", result.loteria());
                    response.put("sincronizados", result.sincronizados());
                    response.put("sucesso", result.sucesso());
                    response.put("mensagem", result.mensagem());
                    response.put("rateLimited", false);
                    response.put("cooldownSeconds", rateLimitService.getCooldownSeconds());
                    return response;
                });
    }

    @GetMapping("/sync-status")
    @Operation(summary = "Status do rate limit de sincronização",
               description = "Retorna o status atual do rate limit para sincronização com a Caixa")
    public Mono<Map<String, Object>> getSyncStatus() {
        var status = rateLimitService.getStatus();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("allowed", status.allowed());
        response.put("remainingSeconds", status.remainingSeconds());
        response.put("cooldownSeconds", rateLimitService.getCooldownSeconds());
        response.put("lastSync", status.lastSync().toString());
        return Mono.just(response);
    }

    @PostMapping("/{tipo}/atualizar-ganhadores")
    @Operation(summary = "Atualizar detalhes de ganhadores", 
               description = "Busca na API da Caixa e atualiza informações detalhadas de concursos que tiveram ganhadores (cidade, UF, canal)")
    public Mono<Map<String, Object>> atualizarGanhadores(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) 
            @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Atualização de ganhadores iniciada para {}", tipoLoteria.getNome());
        
        return Mono.fromCallable(() -> atualizarGanhadoresService.atualizarConcursosComGanhadores(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(atualizados -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", tipoLoteria.getNome());
                    response.put("atualizados", atualizados);
                    response.put("mensagem", String.format("%d concursos atualizados com detalhes de ganhadores", atualizados));
                    return response;
                });
    }

    @PostMapping("/atualizar-ganhadores-all")
    @Operation(summary = "Atualizar ganhadores de todas as loterias", 
               description = "Busca na API da Caixa e atualiza informações de ganhadores de TODAS as loterias usando virtual threads")
    public Mono<Map<String, Object>> atualizarGanhadoresTodos() {
        log.info("Atualização de ganhadores de todas as loterias iniciada (virtual threads)");
        
        return Mono.fromCallable(() -> atualizarGanhadoresService.atualizarTodosComGanhadores())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalAtualizados = resultados.values().stream().mapToInt(Integer::intValue).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("resultados", resultados);
                    response.put("totalAtualizados", totalAtualizados);
                    return response;
                });
    }

    @GetMapping("/backfill-status")
    @Operation(summary = "Status do backfill de faixas de premiação",
               description = "Retorna quantos concursos de cada loteria não possuem faixas de premiação no banco de dados")
    public Mono<Map<String, Object>> getBackfillStatus() {
        return Mono.fromCallable(() -> concursoSyncService.getBackfillStatus())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{tipo}/backfill-faixas")
    @Operation(summary = "Backfill de faixas de premiação",
               description = "Re-busca na API da Caixa e atualiza concursos que não possuem faixas de premiação no banco. Pode demorar minutos para loterias com muitos concursos faltantes.")
    public Mono<Map<String, Object>> backfillFaixas(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Backfill de faixas iniciado para {}", tipoLoteria.getNome());

        return Mono.fromCallable(() -> concursoSyncService.backfillFaixasPremiacao(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", result.loteria());
                    response.put("atualizados", result.atualizados());
                    response.put("erros", result.erros());
                    response.put("totalSemFaixas", result.totalSemFaixas());
                    response.put("mensagem", String.format("Backfill: %d atualizados, %d erros de %d concursos sem faixas",
                            result.atualizados(), result.erros(), result.totalSemFaixas()));
                    return response;
                });
    }

    @PostMapping("/backfill-faixas-all")
    @Operation(summary = "Backfill de faixas de TODAS as loterias",
               description = "Re-busca na API da Caixa todos os concursos sem faixas de premiação para TODAS as loterias. Operação longa.")
    public Mono<Map<String, Object>> backfillFaixasTodos() {
        log.info("Backfill de faixas de todas as loterias iniciado");

        return Mono.fromCallable(() -> concursoSyncService.backfillFaixasTodos())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalAtualizados = resultados.values().stream()
                            .mapToInt(ConcursoSyncService.BackfillResult::atualizados).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("resultados", resultados);
                    response.put("totalAtualizados", totalAtualizados);
                    return response;
                });
    }

    @PostMapping("/{tipo}/backfill-completo")
    @Operation(summary = "Backfill completo (faixas + arrecadação)",
               description = "Re-busca da API da Caixa todos os concursos incompletos (sem faixas OU sem arrecadação). " +
                            "Usa 250ms de pausa entre requisições para evitar rate limit (429).")
    public Mono<Map<String, Object>> backfillCompleto(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        log.info("Backfill completo iniciado para {}", tipoLoteria.getNome());

        return Mono.fromCallable(() -> concursoSyncService.backfillIncompletos(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("tipo", result.loteria());
                    response.put("atualizados", result.atualizados());
                    response.put("erros", result.erros());
                    response.put("totalIncompletos", result.totalSemFaixas());
                    response.put("mensagem", String.format("Backfill completo: %d atualizados, %d erros de %d incompletos",
                            result.atualizados(), result.erros(), result.totalSemFaixas()));
                    return response;
                });
    }

    @PostMapping("/backfill-completo-all")
    @Operation(summary = "Backfill completo de TODAS as loterias",
               description = "Re-busca todos os concursos incompletos de TODAS as loterias. Operação muito longa (pode levar 30+ minutos).")
    public Mono<Map<String, Object>> backfillCompletoTodos() {
        log.info("Backfill completo de todas as loterias iniciado");

        return Mono.fromCallable(() -> concursoSyncService.backfillIncompletosTodos())
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultados -> {
                    int totalAtualizados = resultados.values().stream()
                            .mapToInt(ConcursoSyncService.BackfillResult::atualizados).sum();
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("resultados", resultados);
                    response.put("totalAtualizados", totalAtualizados);
                    return response;
                });
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
