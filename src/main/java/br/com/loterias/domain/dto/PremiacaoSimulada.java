package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PremiacaoSimulada(
    Integer concurso,
    LocalDate data,
    List<Integer> jogoApostado,
    List<Integer> dezenasSorteadas,
    Integer acertos,
    String faixa,
    BigDecimal premio
) {}
