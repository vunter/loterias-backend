package br.com.loterias.domain.dto;

import br.com.loterias.domain.entity.TipoLoteria;

import java.util.List;
import java.util.Map;

public record EstatisticaDTO(
        TipoLoteria tipoLoteria,
        Map<Integer, Long> frequenciaNumeros,
        Map<Integer, Long> numerosMaisFrequentes,
        Map<Integer, Long> numerosMenosFrequentes,
        Map<Integer, Long> numerosAtrasados,
        Double mediaParesProConcurso,
        Double mediaImparesProConcurso,
        Double somaMedia,
        Map<String, Long> distribuicaoPorFaixa,
        Map<String, Long> combinacoesSequenciais,
        List<Integer> concursosDoNumero
) {}
