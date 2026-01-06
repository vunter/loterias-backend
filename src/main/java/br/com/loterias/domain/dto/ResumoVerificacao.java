package br.com.loterias.domain.dto;

import java.math.BigDecimal;

public record ResumoVerificacao(
    Integer totalConcursosVerificados,
    Integer totalAcertos4mais,
    Integer totalAcertos5mais,
    Integer totalPremiacoes,
    BigDecimal valorTotalPremios
) {}
