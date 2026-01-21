package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ConcursosEspeciaisResponse(
    List<LoteriaEspecialInfo> loteriasComEspecial,
    BigDecimal totalAcumuladoEspeciais,
    List<ProximoConcursoEspecialInfo> proximosConcursosEspeciais
) {
    public record LoteriaEspecialInfo(
        String tipo,
        String nome,
        String cor,
        Integer indicadorConcursoEspecial,
        Integer numeroConcursoFinalEspecial,
        BigDecimal valorAcumuladoConcursoEspecial,
        BigDecimal valorAcumuladoConcurso05,
        String nomeEspecial,
        UltimoConcursoInfo ultimoConcurso
    ) {}

    public record UltimoConcursoInfo(
        Integer numero,
        LocalDate data,
        List<Integer> dezenas,
        List<Integer> dezenasOrdemSorteio,
        List<Integer> dezenasSegundoSorteio,
        BigDecimal valorArrecadado,
        BigDecimal valorEstimadoProximo,
        String localSorteio,
        String municipioUFSorteio,
        BigDecimal valorTotalPremioFaixaUm,
        BigDecimal valorSaldoReservaGarantidora
    ) {}

    public record ProximoConcursoEspecialInfo(
        String tipoLoteria,
        String nomeLoteria,
        String nomeEspecial,
        Integer numeroConcursoFinalEspecial,
        Integer concursosFaltando,
        BigDecimal valorAcumulado,
        LocalDate dataEstimada
    ) {}
}
