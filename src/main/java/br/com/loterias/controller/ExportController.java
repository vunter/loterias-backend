package br.com.loterias.controller;

import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/export")
@Tag(name = "Exportação", description = "Exportação de dados e estatísticas em formato CSV")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(value = "/{tipo}/concursos.csv", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Exportar concursos", description = "Exporta todos os resultados de concursos em formato CSV")
    public Mono<ResponseEntity<byte[]>> exportarConcursos(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        log.info("Request exportar concursos CSV: tipo={}", tipo);
        TipoLoteria tipoLoteria = converterTipo(tipo);
        return Mono.fromCallable(() -> exportService.exportarConcursosCSV(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(csv -> criarRespostaCSV(csv, tipoLoteria.getEndpoint() + "-concursos.csv"));
    }

    @GetMapping(value = "/{tipo}/frequencia.csv", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Exportar frequência", description = "Exporta a frequência de cada número em formato CSV")
    public Mono<ResponseEntity<byte[]>> exportarFrequencia(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        log.info("Request exportar frequência CSV: tipo={}", tipo);
        TipoLoteria tipoLoteria = converterTipo(tipo);
        return Mono.fromCallable(() -> exportService.exportarFrequenciaCSV(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(csv -> criarRespostaCSV(csv, tipoLoteria.getEndpoint() + "-frequencia.csv"));
    }

    @GetMapping(value = "/{tipo}/estatisticas.csv", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Exportar estatísticas", description = "Exporta estatísticas gerais da loteria em formato CSV")
    public Mono<ResponseEntity<byte[]>> exportarEstatisticas(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        log.info("Request exportar estatísticas CSV: tipo={}", tipo);
        TipoLoteria tipoLoteria = converterTipo(tipo);
        return Mono.fromCallable(() -> exportService.exportarEstatisticasCSV(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic())
                .map(csv -> criarRespostaCSV(csv, tipoLoteria.getEndpoint() + "-estatisticas.csv"));
    }

    private TipoLoteria converterTipo(String tipo) {
        String normalizado = tipo.toUpperCase().replace("-", "_");
        for (TipoLoteria t : TipoLoteria.values()) {
            if (t.getEndpoint().equalsIgnoreCase(tipo) ||
                    t.name().equalsIgnoreCase(tipo) ||
                    t.name().equalsIgnoreCase(normalizado)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de loteria inválido: " + tipo);
    }

    private ResponseEntity<byte[]> criarRespostaCSV(String csv, String filename) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}
