package br.com.loterias.domain.dto;

import br.com.loterias.domain.entity.TipoLoteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
    TipoLoteria tipo,
    String nomeLoteria,
    ResumoGeral resumo,
    UltimoConcursoInfo ultimoConcurso,
    UltimoConcursoComGanhadorInfo ultimoConcursoComGanhador,
    List<Integer> numerosQuentes,
    List<Integer> numerosFrios,
    List<Integer> numerosAtrasados,
    AnalisePatterns padroes,
    ProximoConcursoInfo proximoConcurso,
    TimeCoracaoInfo timeCoracaoInfo
) {
    public record ResumoGeral(
        int totalConcursos,
        LocalDate primeiroSorteio,
        LocalDate ultimoSorteio,
        int diasSemSorteio,
        BigDecimal maiorPremio,
        int concursoMaiorPremio,
        double mediaPremioFaixaPrincipal
    ) {}

    public record UltimoConcursoInfo(
        int numero,
        LocalDate data,
        List<Integer> dezenas,
        List<Integer> dezenasSegundoSorteio,
        boolean acumulou,
        BigDecimal valorAcumulado,
        int ganhadoresFaixaPrincipal,
        BigDecimal premioFaixaPrincipal,
        List<GanhadorInfo> ganhadores
    ) {}

    public record GanhadorInfo(
        String uf,
        String cidade,
        int quantidade,
        String canal
    ) {}

    public record AnalisePatterns(
        double mediaPares,
        double mediaImpares,
        double mediaPrimos,
        double mediaSoma,
        Map<String, Long> distribuicaoFaixas,
        int maiorSequencia,
        double mediaNumerosBaixos,
        double mediaNumerosAltos
    ) {}

    public record ProximoConcursoInfo(
        int numero,
        LocalDate dataEstimada,
        BigDecimal premioEstimado,
        boolean acumulado
    ) {}

    public record UltimoConcursoComGanhadorInfo(
        int numero,
        LocalDate data,
        List<Integer> dezenas,
        List<Integer> dezenasSegundoSorteio,
        int totalGanhadores,
        BigDecimal premioPorGanhador,
        BigDecimal premioTotal,
        List<GanhadorInfo> ganhadores,
        int concursosDesdeUltimoGanhador
    ) {}

    public record TimeCoracaoInfo(
        String tipoInfo,
        String valorAtual,
        String maisFrequente,
        String menosFrequente,
        String maisAtrasado,
        int atrasoMaisAtrasado,
        List<String> top5
    ) {}
}
