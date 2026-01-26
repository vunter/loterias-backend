package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinanceiroAnalise(
    String tipoLoteria,
    String nomeLoteria,
    ResumoFinanceiro resumo,
    List<DadosMensais> evolucaoMensal,
    List<DadosConcurso> ultimosConcursos
) {
    public record ResumoFinanceiro(
        BigDecimal totalArrecadado,
        BigDecimal totalPremiosPagos,
        BigDecimal maiorArrecadacao,
        int concursoMaiorArrecadacao,
        BigDecimal mediaArrecadacao,
        BigDecimal mediaPremioFaixaUm,
        double percentualRetornoPremios,
        BigDecimal saldoReservaAtual
    ) {}

    public record DadosMensais(
        int ano,
        int mes,
        String mesAno,
        BigDecimal totalArrecadado,
        BigDecimal totalPremios,
        int quantidadeConcursos,
        double roi
    ) {}

    public record DadosConcurso(
        int numero,
        LocalDate data,
        BigDecimal arrecadado,
        BigDecimal premioFaixaUm,
        BigDecimal estimadoProximo,
        int ganhadores
    ) {}
}
