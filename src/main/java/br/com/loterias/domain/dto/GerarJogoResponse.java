package br.com.loterias.domain.dto;

import br.com.loterias.domain.entity.TipoLoteria;

import java.time.LocalDateTime;
import java.util.List;

public record GerarJogoResponse(
    TipoLoteria tipo,
    List<List<Integer>> jogos,
    String estrategia,
    LocalDateTime geradoEm,
    String timeSugerido,
    String mesSugerido,
    List<String> timesSugeridos,
    List<String> mesesSugeridos,
    Integer quantidadeDezenas,
    DebugInfo debug
) {
    public GerarJogoResponse(TipoLoteria tipo, List<List<Integer>> jogos, String estrategia, LocalDateTime geradoEm) {
        this(tipo, jogos, estrategia, geradoEm, null, null, null, null, null, null);
    }

    public GerarJogoResponse(TipoLoteria tipo, List<List<Integer>> jogos, String estrategia, LocalDateTime geradoEm, DebugInfo debug) {
        this(tipo, jogos, estrategia, geradoEm, null, null, null, null, null, debug);
    }

    public GerarJogoResponse(TipoLoteria tipo, List<List<Integer>> jogos, String estrategia, LocalDateTime geradoEm,
                             String timeSugerido, String mesSugerido, DebugInfo debug) {
        this(tipo, jogos, estrategia, geradoEm, timeSugerido, mesSugerido, 
             timeSugerido != null ? List.of(timeSugerido) : null,
             mesSugerido != null ? List.of(mesSugerido) : null, 
             null, debug);
    }

    public record DebugInfo(
        List<String> etapas,
        java.util.Map<Integer, Double> pesosFinais,
        java.util.Map<Integer, Long> frequencias,
        java.util.Map<Integer, Long> atrasos,
        List<Integer> numerosQuentes,
        List<Integer> numerosFrios,
        List<Integer> numerosAtrasados,
        String criteriosUsados,
        List<String> timesTop5,
        List<String> mesesTop5,
        TimeCoracaoDebug timeCoracaoDebug
    ) {
        public DebugInfo(
            List<String> etapas,
            java.util.Map<Integer, Double> pesosFinais,
            java.util.Map<Integer, Long> frequencias,
            java.util.Map<Integer, Long> atrasos,
            List<Integer> numerosQuentes,
            List<Integer> numerosFrios,
            List<Integer> numerosAtrasados,
            String criteriosUsados
        ) {
            this(etapas, pesosFinais, frequencias, atrasos, numerosQuentes, numerosFrios, numerosAtrasados, criteriosUsados, null, null, null);
        }

        public DebugInfo(
            List<String> etapas,
            java.util.Map<Integer, Double> pesosFinais,
            java.util.Map<Integer, Long> frequencias,
            java.util.Map<Integer, Long> atrasos,
            List<Integer> numerosQuentes,
            List<Integer> numerosFrios,
            List<Integer> numerosAtrasados,
            String criteriosUsados,
            List<String> timesTop5,
            List<String> mesesTop5
        ) {
            this(etapas, pesosFinais, frequencias, atrasos, numerosQuentes, numerosFrios, numerosAtrasados, criteriosUsados, timesTop5, mesesTop5, null);
        }
    }

    public record TimeCoracaoDebug(
        String tipo,
        String estrategiaUsada,
        String sugestao,
        String motivo,
        int frequenciaSugestao,
        double percentualSugestao,
        int atrasoSugestao,
        List<ItemRanking> ranking
    ) {
        public record ItemRanking(String nome, int frequencia, double percentual, int atraso) {}
    }
}
