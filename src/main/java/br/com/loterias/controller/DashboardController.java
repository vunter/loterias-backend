package br.com.loterias.controller;

import br.com.loterias.domain.dto.AcumuladoResponse;
import br.com.loterias.domain.dto.AnaliseNumeroResponse;
import br.com.loterias.domain.dto.ConferirApostaResponse;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse;
import br.com.loterias.domain.dto.DashboardResponse;
import br.com.loterias.domain.dto.GanhadoresUFResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.AnaliseNumeroService;
import br.com.loterias.service.ConferirApostaService;
import br.com.loterias.service.ConcursosEspeciaisService;
import br.com.loterias.service.DashboardService;
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

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Visão geral e análises avançadas das loterias")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;
    private final ConferirApostaService conferirApostaService;
    private final AnaliseNumeroService analiseNumeroService;
    private final ConcursosEspeciaisService concursosEspeciaisService;

    public DashboardController(DashboardService dashboardService,
                                ConferirApostaService conferirApostaService,
                                AnaliseNumeroService analiseNumeroService,
                                ConcursosEspeciaisService concursosEspeciaisService) {
        this.dashboardService = dashboardService;
        this.conferirApostaService = conferirApostaService;
        this.analiseNumeroService = analiseNumeroService;
        this.concursosEspeciaisService = concursosEspeciaisService;
    }

    @GetMapping("/{tipo}")
    @Operation(summary = "Dashboard completo", 
               description = "Retorna visão geral completa da loteria: resumo, último concurso, números quentes/frios/atrasados, padrões e previsão do próximo concurso")
    public Mono<DashboardResponse> getDashboard(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request dashboard: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> dashboardService.gerarDashboard(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/conferir")
    @Operation(summary = "Conferir aposta no histórico", 
               description = "Verifica quantas vezes os números escolhidos teriam sido premiados no histórico de concursos")
    public Mono<ConferirApostaResponse> conferirAposta(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo,
            @Parameter(description = "Números da aposta separados por vírgula", example = "4,8,15,16,23,42")
            @RequestParam List<Integer> numeros) {
        log.info("Request conferir aposta: tipo={}, numeros={}", tipo, numeros);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> conferirApostaService.conferirNoHistorico(tipoLoteria, numeros))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/numero/{numero}")
    @Operation(summary = "Análise detalhada de um número", 
               description = "Retorna análise completa de um número específico: frequência, atrasos, tendência e companheiros frequentes")
    public Mono<AnaliseNumeroResponse> analisarNumero(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo,
            @Parameter(description = "Número a ser analisado")
            @PathVariable Integer numero) {
        log.info("Request análise número: tipo={}, numero={}", tipo, numero);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> analiseNumeroService.analisarNumero(tipoLoteria, numero))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/numeros/ranking")
    @Operation(summary = "Ranking de todos os números", 
               description = "Retorna análise de todos os números ordenados por score de tendência")
    public Mono<List<AnaliseNumeroResponse>> rankingNumeros(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request ranking números: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> {
            List<AnaliseNumeroResponse> analises = analiseNumeroService.analisarTodosNumeros(tipoLoteria);
            analises.sort((a, b) -> Integer.compare(b.tendencia().scoreTendencia(), a.tendencia().scoreTendencia()));
            return analises;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/especiais")
    @Operation(summary = "Dashboard de Concursos Especiais", 
               description = "Retorna informações sobre concursos especiais (Mega da Virada, Quina de São João, etc.), valores acumulados e próximos concursos especiais")
    public Mono<ConcursosEspeciaisResponse> getDashboardEspeciais() {
        log.info("Request dashboard concursos especiais");
        return Mono.fromCallable(concursosEspeciaisService::gerarDashboardEspeciais)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/acumulado")
    @Operation(summary = "Status do acumulado",
               description = "Retorna informações sobre o acumulado atual: valor, concursos consecutivos acumulados e estimativa do próximo sorteio")
    public Mono<AcumuladoResponse> getAcumulado(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request acumulado: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> dashboardService.getAcumulado(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/ganhadores-por-uf")
    @Operation(summary = "Ganhadores por estado",
               description = "Retorna a distribuição de ganhadores da faixa principal por estado (UF) e cidade")
    public Mono<GanhadoresUFResponse> getGanhadoresPorUF(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo) {
        log.info("Request ganhadores por UF: tipo={}", tipo);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> dashboardService.getGanhadoresPorUF(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
