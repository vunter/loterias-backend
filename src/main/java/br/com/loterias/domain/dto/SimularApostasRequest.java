package br.com.loterias.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Requisição para simular apostas em concursos históricos")
public record SimularApostasRequest(
    @NotEmpty(message = "Lista de jogos não pode ser vazia")
    @Size(min = 1, max = 100, message = "Número de jogos deve ser entre 1 e 100")
    @Schema(description = "Lista de jogos a simular (cada jogo é uma lista de números)", 
            example = "[[4, 8, 15, 16, 23, 42], [1, 5, 10, 20, 35, 50]]")
    List<List<Integer>> jogos,
    
    @Positive(message = "Concurso inicial deve ser positivo")
    @Schema(description = "Concurso inicial da simulação", example = "1")
    Integer concursoInicio,
    
    @Positive(message = "Concurso final deve ser positivo")
    @Schema(description = "Concurso final da simulação (opcional, usa o último se não informado)", example = "2800")
    Integer concursoFim,
    
    @Positive(message = "Valor da aposta deve ser positivo")
    @Schema(description = "Valor de cada aposta em R$", example = "5.00", defaultValue = "5.00")
    BigDecimal valorAposta
) {}
