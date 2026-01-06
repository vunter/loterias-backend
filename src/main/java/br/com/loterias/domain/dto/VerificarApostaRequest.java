package br.com.loterias.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Requisição para verificar uma aposta contra resultados oficiais")
public record VerificarApostaRequest(
    @NotEmpty(message = "Lista de números não pode ser vazia")
    @Size(min = 1, max = 50, message = "Número de dezenas deve ser entre 1 e 50")
    @Schema(description = "Números apostados", example = "[4, 8, 15, 16, 23, 42]")
    List<Integer> numeros,
    
    @Positive(message = "Concurso inicial deve ser positivo")
    @Schema(description = "Número do concurso inicial (opcional, se não informado verifica o último)", example = "2700")
    Integer concursoInicio,
    
    @Positive(message = "Concurso final deve ser positivo")
    @Schema(description = "Número do concurso final (opcional, para verificar um intervalo)", example = "2750")
    Integer concursoFim
) {}
