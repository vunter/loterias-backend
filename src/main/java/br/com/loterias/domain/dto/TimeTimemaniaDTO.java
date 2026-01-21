package br.com.loterias.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Time do Coração disponível na Timemania")
public record TimeTimemaniaDTO(
    @Schema(description = "Código oficial da Caixa", example = "4")
    Integer codigoCaixa,
    
    @Schema(description = "Nome do time", example = "FLAMENGO")
    String nome,
    
    @Schema(description = "UF do time", example = "RJ")
    String uf,
    
    @Schema(description = "Nome completo (nome/uf)", example = "FLAMENGO/RJ")
    String nomeCompleto
) {}
