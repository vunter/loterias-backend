package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResultadoVerificacao(
    Integer concurso,
    LocalDate data,
    List<Integer> dezenasSorteadas,
    List<Integer> acertos,
    Integer quantidadeAcertos,
    String faixaPremiacao,
    BigDecimal valorPremio
) {}
