package br.com.loterias.service;

import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private static final String SEPARATOR = ";";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String UTF8_BOM = "\uFEFF";

    private final ConcursoRepository concursoRepository;
    private final EstatisticaService estatisticaService;

    public ExportService(ConcursoRepository concursoRepository, EstatisticaService estatisticaService) {
        this.concursoRepository = concursoRepository;
        this.estatisticaService = estatisticaService;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public String exportarConcursosCSV(TipoLoteria tipo) {
        log.info("Exportando concursos CSV: tipo={}", tipo.getNome());
        List<Concurso> concursos = concursoRepository.findByTipoLoteria(tipo);
        concursos.sort(Comparator.comparing(Concurso::getNumero));
        log.debug("Concursos encontrados para exportação: count={}", concursos.size());

        int maxDezenas = tipo.getMinimoNumeros();

        StringBuilder csv = new StringBuilder(concursos.size() * 200);
        csv.append(UTF8_BOM);

        csv.append("numero").append(SEPARATOR).append("data");
        for (int i = 1; i <= maxDezenas; i++) {
            csv.append(SEPARATOR).append("dezena").append(i);
        }
        csv.append(SEPARATOR).append("acumulado").append(SEPARATOR).append("valorArrecadado");
        csv.append("\n");

        for (Concurso concurso : concursos) {
            csv.append(concurso.getNumero()).append(SEPARATOR);
            csv.append(concurso.getDataApuracao() != null ? concurso.getDataApuracao().format(DATE_FORMATTER) : "");

            List<Integer> dezenas = concurso.getDezenasSorteadas();
            for (int i = 0; i < maxDezenas; i++) {
                csv.append(SEPARATOR);
                if (i < dezenas.size()) {
                    csv.append(dezenas.get(i));
                }
            }

            csv.append(SEPARATOR).append(concurso.getAcumulado() != null && concurso.getAcumulado() ? "Sim" : "Não");
            csv.append(SEPARATOR).append(escapeCsv(concurso.getValorArrecadado() != null ? concurso.getValorArrecadado().toPlainString() : ""));
            csv.append("\n");
        }

        return csv.toString();
    }

    public String exportarFrequenciaCSV(TipoLoteria tipo) {
        log.info("Exportando frequência CSV: tipo={}", tipo.getNome());
        Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(tipo);
        long totalSorteios = frequencia.values().stream().mapToLong(Long::longValue).sum();

        StringBuilder csv = new StringBuilder();
        csv.append(UTF8_BOM);
        csv.append("numero").append(SEPARATOR).append("frequencia").append(SEPARATOR).append("percentual\n");

        for (Map.Entry<Integer, Long> entry : frequencia.entrySet()) {
            double percentual = totalSorteios > 0 ? (entry.getValue() * 100.0) / totalSorteios : 0;
            csv.append(entry.getKey()).append(SEPARATOR);
            csv.append(entry.getValue()).append(SEPARATOR);
            csv.append(escapeCsv(String.format("%.2f", percentual).replace(".", ",")));
            csv.append("\n");
        }

        return csv.toString();
    }

    public String exportarEstatisticasCSV(TipoLoteria tipo) {
        log.info("Exportando estatísticas CSV: tipo={}", tipo.getNome());
        StringBuilder csv = new StringBuilder();
        csv.append(UTF8_BOM);

        csv.append("=== NÚMEROS MAIS ATRASADOS ===\n");
        csv.append("numero").append(SEPARATOR).append("concursosAtrasados\n");
        Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(tipo, tipo.getNumerosDezenas());
        for (Map.Entry<Integer, Long> entry : atrasados.entrySet()) {
            csv.append(entry.getKey()).append(SEPARATOR).append(entry.getValue()).append("\n");
        }

        csv.append("\n=== PARES E ÍMPARES ===\n");
        csv.append("tipo").append(SEPARATOR).append("media\n");
        Map<String, Double> paresImpares = estatisticaService.paresImpares(tipo);
        csv.append("pares").append(SEPARATOR).append(String.format("%.2f", paresImpares.get("mediaPares")).replace(".", ",")).append("\n");
        csv.append("ímpares").append(SEPARATOR).append(String.format("%.2f", paresImpares.get("mediaImpares")).replace(".", ",")).append("\n");

        csv.append("\n=== SOMA MÉDIA ===\n");
        csv.append("somaMedia\n");
        csv.append(String.format("%.2f", estatisticaService.somaMedia(tipo)).replace(".", ",")).append("\n");

        csv.append("\n=== DISTRIBUIÇÃO POR FAIXA ===\n");
        csv.append("faixa").append(SEPARATOR).append("frequencia\n");
        Map<String, Long> faixas = estatisticaService.dezenasPorFaixa(tipo);
        for (Map.Entry<String, Long> entry : faixas.entrySet()) {
            csv.append(escapeCsv(entry.getKey())).append(SEPARATOR).append(entry.getValue()).append("\n");
        }

        csv.append("\n=== TOP 10 COMBINAÇÕES SEQUENCIAIS ===\n");
        csv.append("sequencia").append(SEPARATOR).append("frequencia\n");
        Map<String, Long> sequenciais = estatisticaService.combinacoesSequenciais(tipo);
        sequenciais.entrySet().stream().limit(10).forEach(entry ->
            csv.append(escapeCsv(entry.getKey())).append(SEPARATOR).append(entry.getValue()).append("\n")
        );

        csv.append("\n=== TOP 10 PARES MAIS FREQUENTES ===\n");
        csv.append("par").append(SEPARATOR).append("frequencia\n");
        Map<String, Long> correlacao = estatisticaService.correlacaoNumeros(tipo, 10);
        for (Map.Entry<String, Long> entry : correlacao.entrySet()) {
            csv.append(escapeCsv(entry.getKey())).append(SEPARATOR).append(entry.getValue()).append("\n");
        }

        return csv.toString();
    }
}
