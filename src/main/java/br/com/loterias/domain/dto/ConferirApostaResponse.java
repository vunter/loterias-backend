package br.com.loterias.domain.dto;

import br.com.loterias.domain.entity.TipoLoteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ConferirApostaResponse(
    TipoLoteria tipo,
    List<Integer> numerosApostados,
    ResumoConferencia resumo,
    List<ResultadoConferencia> concursosPremiados
) {
    public record ResumoConferencia(
        int totalConcursosAnalisados,
        int vezesPremiado,
        double percentualAcerto,
        BigDecimal totalGanho,
        int maiorAcertos,
        int concursoMaiorAcertos
    ) {}

    public record ResultadoConferencia(
        int numeroConcurso,
        LocalDate dataConcurso,
        List<Integer> dezenasSorteadas,
        List<Integer> acertos,
        int quantidadeAcertos,
        String faixaPremiacao,
        BigDecimal valorPremio
    ) {}
}
