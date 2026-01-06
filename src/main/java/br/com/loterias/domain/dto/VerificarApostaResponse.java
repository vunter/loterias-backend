package br.com.loterias.domain.dto;

import java.util.List;

public record VerificarApostaResponse(
    List<Integer> numerosApostados,
    List<ResultadoVerificacao> resultados,
    ResumoVerificacao resumo
) {}
