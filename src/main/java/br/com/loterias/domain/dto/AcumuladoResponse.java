package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AcumuladoResponse(
    String tipoLoteria,
    String nomeLoteria,
    boolean acumulado,
    BigDecimal valorAcumulado,
    BigDecimal valorEstimadoProximo,
    int concursosAcumulados,
    int ultimoConcurso,
    LocalDate dataUltimoConcurso,
    LocalDate dataEstimadaProximo
) {}
