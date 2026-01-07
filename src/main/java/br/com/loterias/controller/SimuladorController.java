package br.com.loterias.controller;

import br.com.loterias.domain.dto.SimularApostasRequest;
import br.com.loterias.domain.dto.SimularApostasResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.SimuladorApostasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;

@RestController
@RequestMapping("/api/apostas")
@Tag(name = "Simulador", description = "Simulação de apostas em concursos históricos")
public class SimuladorController {

    private static final Logger log = LoggerFactory.getLogger(SimuladorController.class);

    private final SimuladorApostasService simuladorApostasService;

    public SimuladorController(SimuladorApostasService simuladorApostasService) {
        this.simuladorApostasService = simuladorApostasService;
    }

    @PostMapping("/{tipo}/simular")
    @Operation(summary = "Simular apostas", description = "Simula apostas em concursos históricos e calcula o retorno financeiro. Retorna total gasto, total ganho e balanço.",
            responses = @ApiResponse(responseCode = "200", description = "Resultado da simulação com balanço financeiro"))
    public Mono<SimularApostasResponse> simularApostas(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @RequestBody(description = "Jogos e parâmetros da simulação", required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"jogos\": [[4, 8, 15, 16, 23, 42], [1, 5, 10, 20, 35, 50]], \"concursoInicio\": 1, \"concursoFim\": 2800, \"valorAposta\": 5.00}")))
            @org.springframework.web.bind.annotation.RequestBody @Valid SimularApostasRequest request) {
        log.info("Request simular apostas: tipo={}, jogos={}, concursoInicio={}, concursoFim={}", tipo, request.jogos().size(), request.concursoInicio(), request.concursoFim());
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> simuladorApostasService.simularApostas(tipoLoteria, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
