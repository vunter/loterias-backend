package br.com.loterias.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DuplaSenaAnalise(
    int totalConcursosAnalisados,
    ComparacaoSorteios comparacao,
    List<Integer> numerosQuentesPrimeiroSorteio,
    List<Integer> numerosQuentesSegundoSorteio,
    List<Integer> numerosQuentesAmbos,
    List<Integer> numerosExclusivosPrimeiro,
    List<Integer> numerosExclusivosSegundo,
    List<ConcursoDuplaSena> ultimosConcursos,
    EstatisticasCoincidencia coincidencias
) {
    public record ComparacaoSorteios(
        Map<Integer, Integer> frequenciaPrimeiroSorteio,
        Map<Integer, Integer> frequenciaSegundoSorteio,
        double correlacao,
        int mediaCoincidenciasPorConcurso
    ) {}

    public record ConcursoDuplaSena(
        int numero,
        LocalDate data,
        List<Integer> dezenasPrimeiroSorteio,
        List<Integer> dezenasSegundoSorteio,
        int coincidencias,
        BigDecimal premioFaixaUm,
        int ganhadoresPrimeiro,
        int ganhadoresSegundo
    ) {}

    public record EstatisticasCoincidencia(
        int maxCoincidencias,
        int minCoincidencias,
        double mediaCoincidencias,
        Map<Integer, Integer> distribuicaoCoincidencias
    ) {}
}
