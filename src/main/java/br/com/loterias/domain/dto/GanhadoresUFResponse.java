package br.com.loterias.domain.dto;

import java.util.List;

public record GanhadoresUFResponse(
    String tipoLoteria,
    String nomeLoteria,
    int totalConcursosAnalisados,
    int totalGanhadores,
    String cidadesDisponiveisDesde,
    List<EstadoGanhadores> porEstado
) {
    public record EstadoGanhadores(
        String uf,
        int totalGanhadores,
        int totalConcursos,
        List<CidadeGanhadores> cidades
    ) {}

    public record CidadeGanhadores(
        String cidade,
        int totalGanhadores
    ) {}
}
