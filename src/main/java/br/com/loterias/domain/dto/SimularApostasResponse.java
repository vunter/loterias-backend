package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SimularApostasResponse(
    Integer totalConcursosSimulados,
    Integer totalApostas,
    BigDecimal totalInvestido,
    BigDecimal totalPremios,
    BigDecimal lucroOuPrejuizo,
    Double roi,
    List<PremiacaoSimulada> premiacoes,
    Map<String, Integer> distribuicaoAcertos
) {}
