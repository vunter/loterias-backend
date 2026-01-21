package br.com.loterias.domain.dto;

import br.com.loterias.domain.entity.TipoLoteria;

import java.time.LocalDate;
import java.util.List;

public record TimeCoracaoMesSorteResponse(
    TipoLoteria tipo,
    String nomeLoteria,
    String tipoAnalise,
    int totalConcursosAnalisados,
    ItemFrequencia maisFrequente,
    ItemFrequencia menosFrequente,
    List<ItemFrequencia> frequenciaCompleta,
    UltimoSorteio ultimoSorteio
) {
    public record ItemFrequencia(
        String nome,
        int frequencia,
        double percentual,
        int atrasoAtual,
        LocalDate ultimaAparicao
    ) {}

    public record UltimoSorteio(
        int numeroConcurso,
        LocalDate data,
        String timeOuMes
    ) {}
}
