package br.com.loterias.controller;

import br.com.loterias.domain.dto.EstrategiaGeracao;
import br.com.loterias.domain.dto.GerarJogoRequest;
import br.com.loterias.domain.dto.GerarJogoResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.EstatisticaService;
import br.com.loterias.service.GeradorEstrategicoService;
import br.com.loterias.service.GeradorJogosService;
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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estatisticas")
@Tag(name = "Estatísticas", description = "Análises estatísticas dos resultados das loterias")
public class EstatisticaController {

    private static final Logger log = LoggerFactory.getLogger(EstatisticaController.class);

    private final EstatisticaService estatisticaService;
    private final GeradorJogosService geradorJogosService;
    private final GeradorEstrategicoService geradorEstrategicoService;

    public EstatisticaController(EstatisticaService estatisticaService, 
                                  GeradorJogosService geradorJogosService,
                                  GeradorEstrategicoService geradorEstrategicoService) {
        this.estatisticaService = estatisticaService;
        this.geradorJogosService = geradorJogosService;
        this.geradorEstrategicoService = geradorEstrategicoService;
    }

    @GetMapping("/{tipo}/frequencia")
    @Operation(summary = "Frequência de todos os números", description = "Retorna a frequência de sorteio de cada número da loteria",
            responses = @ApiResponse(responseCode = "200", description = "Mapa de número -> frequência",
                    content = @Content(examples = @ExampleObject(value = "{\"1\": 245, \"2\": 238, \"3\": 251, \"4\": 267, \"5\": 243}"))))
    public Mono<Map<Integer, Long>> frequenciaTodosNumeros(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.frequenciaTodosNumeros(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/mais-frequentes")
    @Operation(summary = "Números mais frequentes", description = "Retorna os números sorteados com maior frequência",
            responses = @ApiResponse(responseCode = "200", description = "Top N números mais sorteados",
                    content = @Content(examples = @ExampleObject(value = "{\"10\": 312, \"53\": 298, \"5\": 295, \"23\": 291, \"33\": 288}"))))
    public Mono<Map<Integer, Long>> numerosMaisFrequentes(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Quantidade de números a retornar", example = "10") @Min(1) @Max(100) @RequestParam(defaultValue = "10") int quantidade) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.numerosMaisFrequentes(tipoLoteria, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/menos-frequentes")
    @Operation(summary = "Números menos frequentes", description = "Retorna os números sorteados com menor frequência",
            responses = @ApiResponse(responseCode = "200", description = "Top N números menos sorteados",
                    content = @Content(examples = @ExampleObject(value = "{\"26\": 198, \"55\": 201, \"9\": 205, \"22\": 208, \"15\": 210}"))))
    public Mono<Map<Integer, Long>> numerosMenosFrequentes(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Quantidade de números a retornar", example = "10") @Min(1) @Max(100) @RequestParam(defaultValue = "10") int quantidade) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.numerosMenosFrequentes(tipoLoteria, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/mais-frequentes-com-ganhadores")
    @Operation(summary = "Números mais frequentes em concursos com ganhadores", description = "Retorna os números mais sorteados apenas em concursos que tiveram ganhadores")
    public Mono<Map<Integer, Long>> numerosMaisFrequentesComGanhadores(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int quantidade) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.numerosMaisFrequentesComGanhadores(tipoLoteria, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/atrasados")
    @Operation(summary = "Números atrasados", description = "Retorna os números que há mais tempo não são sorteados (valor = quantidade de concursos sem sair)",
            responses = @ApiResponse(responseCode = "200", description = "Números e há quantos concursos não saem",
                    content = @Content(examples = @ExampleObject(value = "{\"26\": 45, \"55\": 38, \"9\": 32, \"22\": 28, \"15\": 25}"))))
    public Mono<Map<Integer, Long>> numerosAtrasados(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Quantidade de números a retornar", example = "10") @Min(1) @Max(100) @RequestParam(defaultValue = "10") int quantidade) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.numerosAtrasados(tipoLoteria, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/pares-impares")
    @Operation(summary = "Distribuição pares/ímpares", description = "Retorna a proporção média de números pares e ímpares nos sorteios",
            responses = @ApiResponse(responseCode = "200", description = "Média de pares e ímpares por sorteio",
                    content = @Content(examples = @ExampleObject(value = "{\"pares\": 3.12, \"impares\": 2.88}"))))
    public Mono<Map<String, Double>> paresImpares(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.paresImpares(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/soma-media")
    @Operation(summary = "Soma média dos números", description = "Retorna a soma média dos números sorteados em todos os concursos",
            responses = @ApiResponse(responseCode = "200", description = "Soma média das dezenas",
                    content = @Content(examples = @ExampleObject(value = "183.45"))))
    public Mono<Double> somaMedia(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.somaMedia(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/sequenciais")
    @Operation(summary = "Combinações sequenciais", description = "Retorna estatísticas sobre números sequenciais sorteados juntos")
    public Mono<Map<String, Long>> combinacoesSequenciais(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.combinacoesSequenciais(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/faixas")
    @Operation(summary = "Dezenas por faixa", description = "Retorna a distribuição de números por faixas (unidades, dezenas, etc.)")
    public Mono<Map<String, Long>> dezenasPorFaixa(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.dezenasPorFaixa(tipoLoteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/numero/{numero}")
    @Operation(summary = "Histórico de um número", description = "Retorna os concursos em que um número específico foi sorteado")
    public Mono<List<Integer>> historicoNumero(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Número a consultar") @PathVariable Integer numero) {
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);

        if (numero < tipoLoteria.getNumeroInicial() || numero > tipoLoteria.getNumeroFinal()) {
            return Mono.error(new IllegalArgumentException(
                    String.format("Número %d inválido para %s. Deve estar entre %d e %d",
                            numero, tipoLoteria.getNome(), tipoLoteria.getNumeroInicial(), tipoLoteria.getNumeroFinal())));
        }

        return Mono.fromCallable(() -> estatisticaService.historicoNumero(tipoLoteria, numero))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/correlacao")
    @Operation(summary = "Correlação entre números", description = "Retorna as combinações de pares de números mais sorteadas juntas")
    public Mono<Map<String, Long>> correlacaoNumeros(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int quantidade) {
        log.debug("Requisição de correlação de números para {} com quantidade {}", tipo, quantidade);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        return Mono.fromCallable(() -> estatisticaService.correlacaoNumeros(tipoLoteria, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/acompanham/{numero}")
    @Operation(summary = "Números que acompanham", description = "Retorna os números que mais aparecem junto com um número específico")
    public Mono<Map<Integer, Long>> numerosQueAcompanham(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"})) @PathVariable String tipo,
            @Parameter(description = "Número de referência") @PathVariable Integer numero,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int quantidade) {
        log.debug("Requisição de números que acompanham {} para {} com quantidade {}", numero, tipo, quantidade);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);

        if (numero < tipoLoteria.getNumeroInicial() || numero > tipoLoteria.getNumeroFinal()) {
            return Mono.error(new IllegalArgumentException(
                    String.format("Número %d inválido para %s. Deve estar entre %d e %d",
                            numero, tipoLoteria.getNome(), tipoLoteria.getNumeroInicial(), tipoLoteria.getNumeroFinal())));
        }

        return Mono.fromCallable(() -> estatisticaService.numerosQueAcompanham(tipoLoteria, numero, quantidade))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/gerar-jogos")
    @Operation(summary = "Gerar jogos inteligentes", description = "Gera sugestões de jogos baseadas em análise estatística dos resultados históricos")
    public Mono<GerarJogoResponse> gerarJogos(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo,
            @Parameter(description = "Quantidade de números por jogo (usa o padrão da loteria se não informado)")
            @RequestParam(required = false) Integer quantidadeNumeros,
            @Parameter(description = "Quantidade de jogos a gerar")
            @RequestParam(required = false, defaultValue = "1") Integer quantidadeJogos,
            @Parameter(description = "Priorizar números mais frequentes (quentes)")
            @RequestParam(required = false) Boolean usarNumerosQuentes,
            @Parameter(description = "Incluir números menos frequentes (frios)")
            @RequestParam(required = false) Boolean usarNumerosFrios,
            @Parameter(description = "Incluir números que estão há mais tempo sem sair")
            @RequestParam(required = false) Boolean usarNumerosAtrasados,
            @Parameter(description = "Equilibrar quantidade de números pares e ímpares")
            @RequestParam(required = false) Boolean balancearParesImpares,
            @Parameter(description = "Evitar números sequenciais (ex: 10,11,12)")
            @RequestParam(required = false) Boolean evitarSequenciais,
            @Parameter(description = "Números que devem obrigatoriamente estar no jogo (separados por vírgula)")
            @RequestParam(required = false) List<Integer> numerosObrigatorios,
            @Parameter(description = "Números que devem ser excluídos do jogo (separados por vírgula)")
            @RequestParam(required = false) List<Integer> numerosExcluidos,
            @Parameter(description = "Para Timemania: sugerir Time do Coração (quente/frio/atrasado/aleatorio)")
            @RequestParam(required = false) String sugerirTime,
            @Parameter(description = "Para Dia de Sorte: sugerir Mês da Sorte (quente/frio/atrasado/aleatorio)")
            @RequestParam(required = false) String sugerirMes,
            @Parameter(description = "Para +Milionária: quantidade de trevos (2 a 6)")
            @RequestParam(required = false) Integer quantidadeTrevos,
            @Parameter(description = "Mostrar informações de debug sobre o processo de geração")
            @RequestParam(defaultValue = "false") Boolean debug) {
        log.debug("Requisição de geração de jogos para {} (debug: {})", tipo, debug);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        GerarJogoRequest request = new GerarJogoRequest(
                quantidadeNumeros, quantidadeJogos, usarNumerosQuentes, usarNumerosFrios,
                usarNumerosAtrasados, balancearParesImpares, evitarSequenciais,
                numerosObrigatorios, numerosExcluidos, sugerirTime, sugerirMes, quantidadeTrevos);
        return Mono.fromCallable(() -> geradorJogosService.gerarJogos(tipoLoteria, request, debug))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{tipo}/gerar-jogos-estrategico")
    @Operation(summary = "Gerar jogos com estratégia específica", 
               description = "Gera jogos usando uma estratégia estatística específica baseada nos dados históricos")
    public Mono<GerarJogoResponse> gerarJogosEstrategico(
            @Parameter(description = "Tipo da loteria", schema = @Schema(allowableValues = {"mega_sena", "lotofacil", "quina", "lotomania", "timemania", "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"}))
            @PathVariable String tipo,
            @Parameter(description = "Estratégia de geração", schema = @Schema(implementation = EstrategiaGeracao.class))
            @RequestParam(defaultValue = "ALEATORIO") EstrategiaGeracao estrategia,
            @Parameter(description = "Quantidade de jogos a gerar (máximo 10)")
            @RequestParam(defaultValue = "1") Integer quantidade,
            @Parameter(description = "Quantidade de números por jogo (dentro dos limites da modalidade)")
            @RequestParam(required = false) Integer quantidadeNumeros,
            @Parameter(description = "Quantidade de trevos para +Milionária (2 a 6)")
            @RequestParam(required = false) Integer quantidadeTrevos,
            @Parameter(description = "Trevos fixos que devem ser incluídos (ex: 1,3,5)")
            @RequestParam(required = false) String trevosFixos,
            @Parameter(description = "Mostrar informações de debug sobre o processo de geração")
            @RequestParam(defaultValue = "false") Boolean debug) {
        log.info("Gerando {} jogos para {} com estratégia {}, {} números, {} trevos (debug: {})", quantidade, tipo, estrategia, quantidadeNumeros, quantidadeTrevos, debug);
        TipoLoteria tipoLoteria = parseTipoLoteria(tipo);
        List<Integer> trevosFixosList = parseTrevosFixos(trevosFixos);
        return Mono.fromCallable(() -> geradorEstrategicoService.gerarJogos(tipoLoteria, estrategia, quantidade, quantidadeNumeros, quantidadeTrevos, trevosFixosList, debug))
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    private List<Integer> parseTrevosFixos(String trevosFixos) {
        if (trevosFixos == null || trevosFixos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trevosFixos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(t -> t != null && t >= 1 && t <= 6)
                .toList();
    }

    @GetMapping("/estrategias")
    @Operation(summary = "Listar estratégias disponíveis", description = "Retorna todas as estratégias de geração de jogos disponíveis")
    public Mono<List<Map<String, String>>> listarEstrategias() {
        List<Map<String, String>> estrategias = Arrays.stream(EstrategiaGeracao.values())
                .map(e -> Map.of(
                        "codigo", e.name(),
                        "nome", e.getNome(),
                        "descricao", e.getDescricao()))
                .toList();
        return Mono.just(estrategias);
    }

    private TipoLoteria parseTipoLoteria(String tipo) {
        return TipoLoteriaParser.parse(tipo);
    }
}
