package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.dto.FinanceiroAnalise;
import br.com.loterias.domain.dto.FinanceiroAnalise.*;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class FinanceiroService {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroService.class);
    private static final DateTimeFormatter MES_ANO_FMT = DateTimeFormatter.ofPattern("MMM/yy", new Locale("pt", "BR"));
    /** Minimum arrecadação to consider valid — filters out Caixa API rounding artifacts (e.g. 0.01) */
    private static final BigDecimal MIN_ARRECADACAO = BigDecimal.ONE;

    private final ConcursoRepository concursoRepository;

    public FinanceiroService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    @Cacheable(value = CacheConfig.CACHE_FINANCEIRO, key = "#tipo.name() + '-' + #dataInicio + '-' + #dataFim")
    public FinanceiroAnalise analisarFinanceiro(TipoLoteria tipo, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Analisando dados financeiros para {} (período: {} a {})", tipo.getNome(), dataInicio, dataFim);

        List<Concurso> concursos;
        if (dataInicio != null && dataFim != null) {
            concursos = concursoRepository.findByTipoLoteriaWithDezenasAndFaixasBetweenDates(tipo, dataInicio, dataFim);
        } else {
            concursos = concursoRepository.findByTipoLoteriaWithDezenasAndFaixas(tipo);
        }

        if (concursos.isEmpty()) {
            log.info("Nenhum concurso encontrado para análise financeira: tipo={}", tipo.getNome());
            return criarAnaliseVazia(tipo);
        }
        log.debug("Concursos carregados para análise financeira: count={}", concursos.size());

        ResumoFinanceiro resumo = calcularResumo(concursos);
        List<DadosMensais> evolucaoMensal = calcularEvolucaoMensal(concursos);
        List<DadosConcurso> ultimosConcursos = extrairUltimosConcursos(concursos, 50);

        log.info("Análise financeira concluída: tipo={}, totalArrecadado={}, meses={}", tipo.getNome(), resumo.totalArrecadado(), evolucaoMensal.size());

        return new FinanceiroAnalise(
            tipo.name(),
            tipo.getNome(),
            resumo,
            evolucaoMensal,
            ultimosConcursos
        );
    }

    private ResumoFinanceiro calcularResumo(List<Concurso> concursos) {
        BigDecimal totalArrecadado = BigDecimal.ZERO;
        BigDecimal totalPremios = BigDecimal.ZERO;
        BigDecimal totalPremiosFaixaUm = BigDecimal.ZERO;
        BigDecimal maiorArrecadacao = BigDecimal.ZERO;
        int concursoMaiorArrecadacao = 0;
        // Concursos are ordered DESC — first non-null saldo is the latest
        BigDecimal saldoReserva = null;
        int concursosComArrecadacao = 0;
        int concursosComFaixaUm = 0;

        for (Concurso c : concursos) {
            boolean temArrecadacao = c.getValorArrecadado() != null
                    && c.getValorArrecadado().compareTo(MIN_ARRECADACAO) >= 0;

            if (temArrecadacao) {
                totalArrecadado = totalArrecadado.add(c.getValorArrecadado());
                concursosComArrecadacao++;
                if (c.getValorArrecadado().compareTo(maiorArrecadacao) > 0) {
                    maiorArrecadacao = c.getValorArrecadado();
                    concursoMaiorArrecadacao = c.getNumero();
                }
                // Only count prizes for concursos that have arrecadação data
                totalPremios = totalPremios.add(getTotalPremiosPagos(c));
            }

            BigDecimal premioFaixaUm = getPremioFaixaUm(c);
            if (premioFaixaUm != null && premioFaixaUm.compareTo(BigDecimal.ZERO) > 0) {
                totalPremiosFaixaUm = totalPremiosFaixaUm.add(premioFaixaUm);
                concursosComFaixaUm++;
            }

            if (saldoReserva == null && c.getValorSaldoReservaGarantidora() != null) {
                saldoReserva = c.getValorSaldoReservaGarantidora();
            }
        }

        BigDecimal mediaArrecadacao = concursosComArrecadacao > 0
            ? totalArrecadado.divide(BigDecimal.valueOf(concursosComArrecadacao), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal mediaPremioFaixaUm = concursosComFaixaUm > 0
            ? totalPremiosFaixaUm.divide(BigDecimal.valueOf(concursosComFaixaUm), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        double percentualRetorno = totalArrecadado.compareTo(BigDecimal.ZERO) > 0
            ? totalPremios.multiply(BigDecimal.valueOf(100)).divide(totalArrecadado, 2, RoundingMode.HALF_UP).doubleValue()
            : 0;

        return new ResumoFinanceiro(
            totalArrecadado,
            totalPremios,
            maiorArrecadacao,
            concursoMaiorArrecadacao,
            mediaArrecadacao,
            mediaPremioFaixaUm,
            percentualRetorno,
            saldoReserva != null ? saldoReserva : BigDecimal.ZERO
        );
    }

    private List<DadosMensais> calcularEvolucaoMensal(List<Concurso> concursos) {
        Map<String, List<Concurso>> porMes = concursos.stream()
                .filter(c -> c.getDataApuracao() != null)
                .collect(Collectors.groupingBy(c -> {
                    LocalDate data = c.getDataApuracao();
                    return String.format("%d-%02d", data.getYear(), data.getMonthValue());
                }));

        return porMes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    int ano = Integer.parseInt(parts[0]);
                    int mes = Integer.parseInt(parts[1]);

                    // Only include concursos with real arrecadação data
                    List<Concurso> comArrecadacao = e.getValue().stream()
                            .filter(c -> c.getValorArrecadado() != null
                                    && c.getValorArrecadado().compareTo(MIN_ARRECADACAO) >= 0)
                            .toList();

                    BigDecimal arrecadado = comArrecadacao.stream()
                            .map(Concurso::getValorArrecadado)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    BigDecimal premios = comArrecadacao.stream()
                            .map(this::getTotalPremiosPagos)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double roi = arrecadado.compareTo(BigDecimal.ZERO) > 0
                            ? premios.multiply(BigDecimal.valueOf(100)).divide(arrecadado, 2, RoundingMode.HALF_UP).doubleValue()
                            : 0;

                    String mesAno = LocalDate.of(ano, mes, 1).format(MES_ANO_FMT);

                    return new DadosMensais(ano, mes, mesAno, arrecadado, premios, comArrecadacao.size(), roi);
                })
                .filter(m -> m.quantidadeConcursos() > 0)
                .collect(Collectors.toList());
    }

    private List<DadosConcurso> extrairUltimosConcursos(List<Concurso> concursos, int limite) {
        return concursos.stream()
                .sorted(Comparator.comparingInt(Concurso::getNumero).reversed())
                .limit(limite)
                .map(c -> new DadosConcurso(
                    c.getNumero(),
                    c.getDataApuracao(),
                    c.getValorArrecadado(),
                    getPremioFaixaUm(c),
                    c.getValorEstimadoProximoConcurso(),
                    getGanhadoresFaixaUm(c)
                ))
                .toList();
    }

    private BigDecimal getPremioFaixaUm(Concurso c) {
        return c.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst()
                .map(FaixaPremiacao::getValorPremio)
                .orElse(null);
    }

    /** Sums premio * ganhadores across ALL faixas for accurate total payout */
    private BigDecimal getTotalPremiosPagos(Concurso c) {
        BigDecimal total = BigDecimal.ZERO;
        for (FaixaPremiacao f : c.getFaixasPremiacao()) {
            if (f.getValorPremio() != null && f.getNumeroGanhadores() != null && f.getNumeroGanhadores() > 0) {
                total = total.add(f.getValorPremio().multiply(BigDecimal.valueOf(f.getNumeroGanhadores())));
            }
        }
        return total;
    }

    private int getGanhadoresFaixaUm(Concurso c) {
        return c.getFaixasPremiacao().stream()
                .filter(f -> f.getFaixa() == 1)
                .findFirst()
                .map(f -> f.getNumeroGanhadores() != null ? f.getNumeroGanhadores() : 0)
                .orElse(0);
    }

    private FinanceiroAnalise criarAnaliseVazia(TipoLoteria tipo) {
        return new FinanceiroAnalise(
            tipo.name(),
            tipo.getNome(),
            new ResumoFinanceiro(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO),
            List.of(),
            List.of()
        );
    }
}
