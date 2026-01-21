package br.com.loterias.domain.dto;

import java.math.BigDecimal;

public record RateioPremioDTO(
    Integer faixa,
    String descricaoFaixa,
    Integer numeroDeGanhadores,
    BigDecimal valorPremio
) {}
