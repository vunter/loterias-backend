package br.com.loterias.controller;

import br.com.loterias.domain.dto.DuplaSenaAnalise;
import br.com.loterias.domain.dto.FinanceiroAnalise;
import br.com.loterias.domain.dto.OrdemSorteioAnalise;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.DuplaSenaService;
import br.com.loterias.service.FinanceiroService;
import br.com.loterias.service.OrdemSorteioService;
import br.com.loterias.service.TendenciaAnaliseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/analise")
@Tag(name = "Análise Avançada", description = "Análises avançadas: ordem de sorteio, financeiro, Dupla Sena")
public class AnaliseAvancadaController {

    private static final Logger log = LoggerFactory.getLogger(AnaliseAvancadaController.class);

    private final OrdemSorteioService ordemSorteioService;
    private final FinanceiroService financeiroService;
    private final DuplaSenaService duplaSenaService;
    private final TendenciaAnaliseService tendenciaAnaliseService;

    public AnaliseAvancadaController(OrdemSorteioService ordemSorteioService,
                                      FinanceiroService financeiroService,
                                      DuplaSenaService duplaSenaService,
                                      TendenciaAnaliseService tendenciaAnaliseService) {
        this.ordemSorteioService = ordemSorteioService;
        this.financeiroService = financeiroService;
        this.duplaSenaService = duplaSenaService;
        this.tendenciaAnaliseService = tendenciaAnaliseService;
    }

    @GetMapping("/{tipo}/ordem-sorteio")
    @Operation(summary = "Análise de ordem de sorteio",
               description = "Analisa quais números saem primeiro/último com mais frequência, posição média de cada número")
    public Mono<OrdemSorteioAnalise> analisarOrdemSorteio(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request análise ordem de sorteio: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> ordemSorteioService.analisarOrdemSorteio(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/financeiro")
    @Operation(summary = "Análise financeira",
               description = "Analisa arrecadação, prêmios pagos, ROI, evolução mensal. Filtro por período opcional via dataInicio/dataFim (ISO 8601).")
    public Mono<FinanceiroAnalise> analisarFinanceiro(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo,
            @Parameter(description = "Data início (ISO 8601, ex: 2024-01-01)")
            @RequestParam(required = false) LocalDate dataInicio,
            @Parameter(description = "Data fim (ISO 8601, ex: 2025-12-31)")
            @RequestParam(required = false) LocalDate dataFim) {
        log.info("Request análise financeira: tipo={}, dataInicio={}, dataFim={}", tipo, dataInicio, dataFim);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> financeiroService.analisarFinanceiro(tipoLoteria, dataInicio, dataFim))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/dupla-sena")
    @Operation(summary = "Análise Dupla Sena",
               description = "Compara primeiro e segundo sorteio da Dupla Sena: frequências, coincidências, correlação")
    public Mono<DuplaSenaAnalise> analisarDuplaSena() {
        log.info("Request análise Dupla Sena");
        return Mono.fromCallable(duplaSenaService::analisarDuplaSena)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/tendencias")
    @Operation(summary = "Análise de tendências",
               description = "Identifica números quentes, frios, emergentes e padrões vencedores")
    public Mono<TendenciaAnaliseService.TendenciaResponse> analisarTendencias(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request análise de tendências: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> tendenciaAnaliseService.analisarTendencias(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/historico-mensal")
    @Operation(summary = "Histórico mensal de frequência",
               description = "Mostra a evolução mensal da frequência de cada número")
    public Mono<List<TendenciaAnaliseService.HistoricoMensal>> historicoMensal(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request histórico mensal: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> tendenciaAnaliseService.historicoMensalFrequencia(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
