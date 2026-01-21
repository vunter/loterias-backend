package br.com.loterias.service;

import br.com.loterias.config.CacheConfig;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse.*;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ConcursosEspeciaisService {

    private static final Logger log = LoggerFactory.getLogger(ConcursosEspeciaisService.class);

    private static final Map<String, String> CORES_LOTERIAS = Map.of(
        "MEGA_SENA", "#209869",
        "LOTOFACIL", "#930089",
        "QUINA", "#260085",
        "LOTOMANIA", "#F78100",
        "TIMEMANIA", "#00FF48",
        "DUPLA_SENA", "#A61324",
        "DIA_DE_SORTE", "#CB8529",
        "SUPER_SETE", "#A8CF45",
        "MAIS_MILIONARIA", "#00346C"
    );

    // Loterias que possuem concursos especiais anuais
    private static final Set<TipoLoteria> LOTERIAS_COM_ESPECIAL = Set.of(
        TipoLoteria.MEGA_SENA,      // Mega da Virada (31/12)
        TipoLoteria.LOTOFACIL,      // Lotofácil da Independência (07/09)
        TipoLoteria.QUINA,          // Quina de São João (24/06)
        TipoLoteria.DUPLA_SENA,     // Dupla de Páscoa (data variável)
        TipoLoteria.LOTOMANIA,      // Lotomania de Páscoa (data variável)
        TipoLoteria.DIA_DE_SORTE    // Dia de Sorte de São João (24/06)
    );

    private final ConcursoRepository concursoRepository;

    public ConcursosEspeciaisService(ConcursoRepository concursoRepository) {
        this.concursoRepository = concursoRepository;
    }

    @Cacheable(value = CacheConfig.CACHE_ESPECIAIS, key = "'dashboard'")
    public ConcursosEspeciaisResponse gerarDashboardEspeciais() {
        log.info("Gerando dashboard de concursos especiais");

        // Single query to load latest concurso per lottery type (scalar fields only)
        List<Concurso> latestList = concursoRepository.findLatestConcursoPerTipoLoteria();
        // Batch-load dezenas collections in parallel for all latest concursos
        Map<TipoLoteria, Concurso> latestByTipo = new java.util.concurrent.ConcurrentHashMap<>();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = latestList.stream()
                    .map(c -> java.util.concurrent.CompletableFuture.runAsync(() ->
                        concursoRepository.findWithCollectionsByTipoAndNumero(c.getTipoLoteria(), c.getNumero())
                                .ifPresent(full -> latestByTipo.put(full.getTipoLoteria(), full)), executor))
                    .toList();
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        }

        List<LoteriaEspecialInfo> loteriasInfo = new ArrayList<>();
        List<ProximoConcursoEspecialInfo> proximosEspeciais = new ArrayList<>();
        BigDecimal totalAcumulado = BigDecimal.ZERO;

        log.debug("Processando {} tipos de loteria para concursos especiais", TipoLoteria.values().length);

        for (TipoLoteria tipo : TipoLoteria.values()) {
            Concurso ultimo = latestByTipo.get(tipo);
            if (ultimo == null) continue;
            
            // Determina se esta loteria tem concurso especial
            boolean temEspecial = LOTERIAS_COM_ESPECIAL.contains(tipo);
            String nomeEspecial = temEspecial ? getNomeEspecial(tipo) : null;
            
            // O valor acumulado do especial vem do campo valorAcumuladoConcursoEspecial
            BigDecimal valorEspecial = ultimo.getValorAcumuladoConcursoEspecial();
            if (valorEspecial == null) {
                valorEspecial = BigDecimal.ZERO;
            }
            
            if (valorEspecial.compareTo(BigDecimal.ZERO) > 0) {
                totalAcumulado = totalAcumulado.add(valorEspecial);
            }

            UltimoConcursoInfo ultimoInfo = new UltimoConcursoInfo(
                ultimo.getNumero(),
                ultimo.getDataApuracao(),
                ultimo.getDezenasSorteadas(),
                ultimo.getDezenasSorteadasOrdemSorteio(),
                ultimo.getDezenasSegundoSorteio().isEmpty() ? null : ultimo.getDezenasSegundoSorteio(),
                ultimo.getValorArrecadado(),
                ultimo.getValorEstimadoProximoConcurso(),
                ultimo.getLocalSorteio(),
                ultimo.getNomeMunicipioUFSorteio(),
                ultimo.getValorTotalPremioFaixaUm(),
                ultimo.getValorSaldoReservaGarantidora()
            );

            // O indicador e número final de concurso especial vem da API
            // indicadorConcursoEspecial: 1 = estamos em período de acumulação para especial
            // numeroConcursoFinalEspecial: número do concurso especial (da API: numeroConcursoFinal_0_5)
            Integer indicador = ultimo.getIndicadorConcursoEspecial();
            Integer numeroFinalEspecial = ultimo.getNumeroConcursoFinalEspecial();
            
            LoteriaEspecialInfo info = new LoteriaEspecialInfo(
                tipo.name(),
                tipo.getNome(),
                CORES_LOTERIAS.getOrDefault(tipo.name(), "#666666"),
                indicador,
                numeroFinalEspecial,
                ultimo.getValorAcumuladoConcursoEspecial(),
                ultimo.getValorAcumuladoConcurso05(),
                nomeEspecial,
                ultimoInfo
            );

            loteriasInfo.add(info);

            // Só adiciona aos próximos especiais se:
            // 1. A loteria tem concurso especial
            // 2. Há indicação de acumulação para especial OU há valor acumulado
            // 3. O número do concurso final é maior que o atual
            if (temEspecial && valorEspecial.compareTo(BigDecimal.ZERO) > 0) {
                // Calcular a data estimada do concurso especial
                LocalDate dataEspecial = calcularDataConcursoEspecial(tipo, ultimo);
                int concursosFaltando = estimarConcursosFaltando(tipo, ultimo, dataEspecial);
                
                if (concursosFaltando > 0 && dataEspecial != null) {
                    proximosEspeciais.add(new ProximoConcursoEspecialInfo(
                        tipo.name(),
                        tipo.getNome(),
                        nomeEspecial,
                        numeroFinalEspecial,
                        concursosFaltando,
                        valorEspecial,
                        dataEspecial
                    ));
                }
            }
        }

        // Ordenar por data estimada (mais próximo primeiro)
        proximosEspeciais.sort((a, b) -> {
            if (a.dataEstimada() == null && b.dataEstimada() == null) return 0;
            if (a.dataEstimada() == null) return 1;
            if (b.dataEstimada() == null) return -1;
            return a.dataEstimada().compareTo(b.dataEstimada());
        });

        log.info("Dashboard especiais gerado: loterias={}, proximosEspeciais={}, totalAcumulado={}", loteriasInfo.size(), proximosEspeciais.size(), totalAcumulado);

        return new ConcursosEspeciaisResponse(loteriasInfo, totalAcumulado, proximosEspeciais);
    }

    private String getNomeEspecial(TipoLoteria tipo) {
        return switch (tipo) {
            case MEGA_SENA -> "Mega da Virada";
            case QUINA -> "Quina de São João";
            case LOTOFACIL -> "Lotofácil da Independência";
            case LOTOMANIA -> "Lotomania de Páscoa";
            case DUPLA_SENA -> "Dupla de Páscoa";
            case DIA_DE_SORTE -> "Dia de Sorte de São João";
            default -> null;
        };
    }

    /**
     * Calcula a data do próximo concurso especial baseado no tipo de loteria.
     * 
     * - Mega da Virada: 31 de dezembro
     * - Lotofácil da Independência: 7 de setembro (ou próximo dia útil)
     * - Quina de São João: 24 de junho
     * - Dia de Sorte de São João: 24 de junho
     * - Dupla de Páscoa / Lotomania de Páscoa: Sábado de Aleluia (varia)
     */
    private LocalDate calcularDataConcursoEspecial(TipoLoteria tipo, Concurso ultimo) {
        LocalDate hoje = LocalDate.now();
        int anoAtual = hoje.getYear();
        
        return switch (tipo) {
            case MEGA_SENA -> {
                // Mega da Virada é sempre 31/12
                LocalDate virada = LocalDate.of(anoAtual, Month.DECEMBER, 31);
                // Se já passou, pega do próximo ano
                if (hoje.isAfter(virada)) {
                    virada = LocalDate.of(anoAtual + 1, Month.DECEMBER, 31);
                }
                yield virada;
            }
            case LOTOFACIL -> {
                // Lotofácil da Independência é próximo de 7/9
                LocalDate independencia = LocalDate.of(anoAtual, Month.SEPTEMBER, 7);
                // Ajustar para sábado mais próximo (Lotofácil sorteia seg-sáb)
                if (hoje.isAfter(independencia.plusDays(7))) {
                    independencia = LocalDate.of(anoAtual + 1, Month.SEPTEMBER, 7);
                }
                yield ajustarParaDiaSorteio(independencia, tipo);
            }
            case QUINA, DIA_DE_SORTE -> {
                // Quina de São João e Dia de Sorte de São João: 24/06
                LocalDate saoJoao = LocalDate.of(anoAtual, Month.JUNE, 24);
                if (hoje.isAfter(saoJoao.plusDays(7))) {
                    saoJoao = LocalDate.of(anoAtual + 1, Month.JUNE, 24);
                }
                yield ajustarParaDiaSorteio(saoJoao, tipo);
            }
            case DUPLA_SENA, LOTOMANIA -> {
                // Dupla de Páscoa e Lotomania de Páscoa: Sábado de Aleluia
                LocalDate pascoa = calcularPascoa(anoAtual);
                LocalDate sabadoAleluia = pascoa.minusDays(1); // Sábado antes do Domingo de Páscoa
                if (hoje.isAfter(sabadoAleluia.plusDays(7))) {
                    pascoa = calcularPascoa(anoAtual + 1);
                    sabadoAleluia = pascoa.minusDays(1);
                }
                yield sabadoAleluia;
            }
            default -> null;
        };
    }

    /**
     * Estima quantos concursos faltam até a data do especial.
     */
    private int estimarConcursosFaltando(TipoLoteria tipo, Concurso ultimo, LocalDate dataEspecial) {
        if (dataEspecial == null || ultimo.getDataApuracao() == null) {
            return 0;
        }

        LocalDate dataUltimo = ultimo.getDataApuracao();
        
        // Se a data do especial já passou ou é hoje, retorna 0
        if (!dataEspecial.isAfter(dataUltimo)) {
            return 0;
        }

        // Calcula dias até o especial
        long diasAteEspecial = dataUltimo.until(dataEspecial, ChronoUnit.DAYS);
        
        // Estima concursos por semana baseado na frequência da loteria
        double concursosPorSemana = getConcursosPorSemana(tipo);
        
        // Calcula semanas e converte para concursos
        double semanas = diasAteEspecial / 7.0;
        int concursosEstimados = (int) Math.round(semanas * concursosPorSemana);
        
        return Math.max(1, concursosEstimados);
    }

    private double getConcursosPorSemana(TipoLoteria tipo) {
        return switch (tipo) {
            case LOTOFACIL, QUINA -> 6.0;  // Segunda a sábado
            case MEGA_SENA, TIMEMANIA, DIA_DE_SORTE -> 3.0;  // 3x por semana
            case LOTOMANIA, DUPLA_SENA, SUPER_SETE -> 3.0;  // 3x por semana
            case MAIS_MILIONARIA -> 2.0;  // Quarta e Sábado
        };
    }

    /**
     * Ajusta a data para um dia válido de sorteio da loteria.
     */
    private LocalDate ajustarParaDiaSorteio(LocalDate data, TipoLoteria tipo) {
        DayOfWeek dia = data.getDayOfWeek();
        
        // Para loterias que sorteiam sábado (maioria), ajusta para o sábado mais próximo
        return switch (tipo) {
            case MEGA_SENA, TIMEMANIA, DIA_DE_SORTE -> {
                // Sorteiam Ter, Qui, Sáb - ajustar para sábado
                yield data.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            }
            case LOTOFACIL, QUINA -> {
                // Sorteiam Seg-Sáb, ajustar para sábado se for domingo
                if (dia == DayOfWeek.SUNDAY) {
                    yield data.plusDays(6); // Próximo sábado
                }
                yield data;
            }
            case LOTOMANIA, DUPLA_SENA, SUPER_SETE -> {
                // Sorteiam Seg, Qua, Sex
                yield data.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            }
            default -> data;
        };
    }

    /**
     * Calcula a data da Páscoa usando o algoritmo de Computus (Anônimo Gregoriano).
     */
    private LocalDate calcularPascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        
        return LocalDate.of(ano, mes, dia);
    }
}
