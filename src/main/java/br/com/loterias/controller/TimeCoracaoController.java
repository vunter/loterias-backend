package br.com.loterias.controller;

import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse;
import br.com.loterias.domain.dto.TimeTimemaniaDTO;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.TimeCoracaoMesSorteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analise")
@Tag(name = "Time do Coração / Mês da Sorte", description = "Análises estatísticas de Time do Coração (Timemania) e Mês da Sorte (Dia de Sorte)")
public class TimeCoracaoController {

    private static final Logger log = LoggerFactory.getLogger(TimeCoracaoController.class);

    private final TimeCoracaoMesSorteService timeCoracaoMesSorteService;

    public TimeCoracaoController(TimeCoracaoMesSorteService timeCoracaoMesSorteService) {
        this.timeCoracaoMesSorteService = timeCoracaoMesSorteService;
    }

    @GetMapping("/timemania/times")
    @Operation(summary = "Lista times ativos da Timemania",
               description = "Retorna todos os times disponíveis atualmente no volante da Timemania")
    public Mono<List<TimeTimemaniaDTO>> listarTimesAtivos() {
        log.info("Request listar times ativos Timemania");
        return Mono.fromCallable(() -> 
                timeCoracaoMesSorteService.listarTimesAtivos().stream()
                        .map(t -> new TimeTimemaniaDTO(t.getCodigoCaixa(), t.getNome(), t.getUf(), t.getNomeCompleto()))
                        .toList()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/time-coracao")
    @Operation(summary = "Análise de Time do Coração / Mês da Sorte",
               description = "Retorna análise completa de frequência dos times (Timemania) ou meses (Dia de Sorte)")
    public Mono<TimeCoracaoMesSorteResponse> analisarTimeCoracao(
            @Parameter(description = "Tipo da loteria (apenas timemania ou dia_de_sorte)",
                       schema = @Schema(allowableValues = {"timemania", "dia_de_sorte"}))
            @PathVariable String tipo) {
        log.info("Request análise time do coração: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> timeCoracaoMesSorteService.analisarTimeCoracao(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/time-coracao/sugestao")
    @Operation(summary = "Sugestão de Time / Mês",
               description = "Sugere um time ou mês baseado na estratégia escolhida: quente (mais frequente), frio (menos frequente), atrasado (maior tempo sem aparecer) ou aleatorio")
    public Mono<Map<String, Object>> sugerirTimeOuMes(
            @Parameter(description = "Tipo da loteria (apenas timemania ou dia_de_sorte)",
                       schema = @Schema(allowableValues = {"timemania", "dia_de_sorte"}))
            @PathVariable String tipo,
            @Parameter(description = "Estratégia de sugestão",
                       schema = @Schema(allowableValues = {"quente", "frio", "atrasado", "aleatorio"}))
            @RequestParam(defaultValue = "quente") String estrategia) {
        log.info("Request sugestão time/mês: tipo={}, estrategia={}", tipo, estrategia);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> timeCoracaoMesSorteService.sugerirTimeOuMes(tipoLoteria, estrategia))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
