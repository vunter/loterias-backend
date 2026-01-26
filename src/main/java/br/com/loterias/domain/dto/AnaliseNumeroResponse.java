package br.com.loterias.domain.dto;

import java.time.LocalDate;
import java.util.List;

public record AnaliseNumeroResponse(
    int numero,
    String tipoLoteria,
    EstatisticasNumero estatisticas,
    List<Integer> ultimasCincoAparicoes,
    List<Integer> companheirosFrequentes,
    TendenciaNumero tendencia
) {
    public record EstatisticasNumero(
        long frequenciaTotal,
        double percentualAparicoes,
        int atrasoAtual,
        int maiorAtraso,
        double mediaAtraso,
        LocalDate primeiraAparicao,
        LocalDate ultimaAparicao
    ) {}

    public record TendenciaNumero(
        String status,
        String recomendacao,
        int scoreTendencia
    ) {}
}
