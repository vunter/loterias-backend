package br.com.loterias.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Parâmetros para geração inteligente de jogos")
public record GerarJogoRequest(
    @Min(1) @Max(50)
    @Schema(description = "Quantidade de números por jogo (usa o padrão da loteria se não informado)", example = "6")
    Integer quantidadeNumeros,
    
    @Min(1) @Max(100)
    @Schema(description = "Quantidade de jogos a gerar", example = "5", defaultValue = "1")
    Integer quantidadeJogos,
    
    @Schema(description = "Priorizar números mais frequentes (quentes)", example = "true", defaultValue = "true")
    Boolean usarNumerosQuentes,
    
    @Schema(description = "Incluir números menos frequentes (frios)", example = "false", defaultValue = "false")
    Boolean usarNumerosFrios,
    
    @Schema(description = "Incluir números que estão há mais tempo sem sair", example = "true", defaultValue = "false")
    Boolean usarNumerosAtrasados,
    
    @Schema(description = "Equilibrar quantidade de números pares e ímpares", example = "true", defaultValue = "true")
    Boolean balancearParesImpares,
    
    @Schema(description = "Evitar números sequenciais (ex: 10,11,12)", example = "true", defaultValue = "false")
    Boolean evitarSequenciais,
    
    @Size(max = 15)
    @Schema(description = "Números que devem obrigatoriamente estar no jogo", example = "[7, 13, 25]")
    List<Integer> numerosObrigatorios,
    
    @Size(max = 15)
    @Schema(description = "Números que devem ser excluídos do jogo", example = "[1, 60]")
    List<Integer> numerosExcluidos,
    
    @Schema(description = "Para Timemania: sugerir Time do Coração (quente/frio/atrasado/aleatorio)", example = "quente")
    String sugerirTime,
    
    @Schema(description = "Para Dia de Sorte: sugerir Mês da Sorte (quente/frio/atrasado/aleatorio)", example = "atrasado")
    String sugerirMes,
    
    @Min(2) @Max(6)
    @Schema(description = "Para +Milionária: quantidade de trevos (2 a 6)", example = "2", defaultValue = "2")
    Integer quantidadeTrevos
) {}
