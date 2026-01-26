package br.com.loterias.domain.dto;

import java.util.List;
import java.util.Map;

public record OrdemSorteioAnalise(
    String tipoLoteria,
    String nomeLoteria,
    int totalConcursosAnalisados,
    List<NumeroOrdemInfo> primeiraBola,
    List<NumeroOrdemInfo> ultimaBola,
    Map<Integer, List<PosicaoFrequencia>> frequenciaPorNumero,
    List<NumeroOrdemInfo> mediaOrdem
) {
    public record NumeroOrdemInfo(
        int numero,
        int frequencia,
        double percentual
    ) {}

    public record PosicaoFrequencia(
        int posicao,
        int frequencia,
        double percentual
    ) {}
}
