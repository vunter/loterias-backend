package br.com.loterias.controller;

import br.com.loterias.domain.dto.VerificarApostaRequest;
import br.com.loterias.domain.dto.VerificarApostaResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.VerificadorApostasService;
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
@Tag(name = "Apostas", description = "Verificação de apostas contra resultados oficiais")
public class ApostasController {

    private static final Logger log = LoggerFactory.getLogger(ApostasController.class);

    private final VerificadorApostasService verificadorApostasService;

    public ApostasController(VerificadorApostasService verificadorApostasService) {
        this.verificadorApostasService = verificadorApostasService;
    }

    @PostMapping("/{tipo}/verificar")
    @Operation(summary = "Verificar aposta", description = "Verifica uma aposta contra os resultados de um ou mais concursos e retorna os acertos",
            responses = @ApiResponse(responseCode = "200", description = "Resultado da verificação com acertos por concurso"))
    public Mono<VerificarApostaResponse> verificarAposta(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @RequestBody(description = "Dados da aposta a verificar", required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"numeros\": [4, 8, 15, 16, 23, 42], \"concursoInicio\": 2700, \"concursoFim\": 2750}")))
            @org.springframework.web.bind.annotation.RequestBody @Valid VerificarApostaRequest request) {
        log.info("Request verificar aposta: tipo={}, numeros={}, concursoInicio={}, concursoFim={}", tipo, request.numeros(), request.concursoInicio(), request.concursoFim());
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> verificadorApostasService.verificarAposta(tipoLoteria, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
