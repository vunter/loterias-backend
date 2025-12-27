package br.com.loterias.service;

import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.GanhadorUF;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.dto.RateioPremioDTO;
import br.com.loterias.domain.dto.GanhadorDTO;
import br.com.loterias.service.util.TextCleaningUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ConcursoMapper {

    private static final Logger log = LoggerFactory.getLogger(ConcursoMapper.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Concurso toEntity(CaixaApiResponse response, TipoLoteria tipoLoteria) {
        Concurso concurso = new Concurso();
        concurso.setTipoLoteria(tipoLoteria);
        concurso.setNumero(response.numero());
        mapCoreFields(concurso, response);
        mapFaixasPremiacao(response, concurso);
        mapGanhadoresUF(response, concurso);
        return concurso;
    }

    private void mapCoreFields(Concurso concurso, CaixaApiResponse response) {
        concurso.setDataApuracao(parseData(response.dataApuracao()));
        concurso.setDezenasSorteadas(parseDezenas(response.listaDezenas()));
        concurso.setDezenasSorteadasOrdemSorteio(parseDezenas(response.dezenasSorteadasOrdemSorteio()));
        concurso.setDezenasSegundoSorteio(parseDezenas(response.listaDezenasSegundoSorteio()));
        concurso.setAcumulado(response.acumulado());
        concurso.setValorArrecadado(response.valorArrecadado());
        concurso.setValorAcumuladoProximoConcurso(response.valorAcumuladoProximoConcurso());
        concurso.setValorEstimadoProximoConcurso(response.valorEstimadoProximoConcurso());
        concurso.setLocalSorteio(response.localSorteio());
        concurso.setNomeMunicipioUFSorteio(response.nomeMunicipioUFSorteio());
        concurso.setNomeTimeCoracaoMesSorte(TextCleaningUtils.cleanNomeTimeCoracao(response.nomeTimeCoracaoMesSorte()));
        concurso.setObservacao(response.observacao());
        concurso.setDataProximoConcurso(parseData(response.dataProximoConcurso()));
        concurso.setNumeroConcursoProximo(response.numeroConcursoProximo());
        concurso.setNumeroConcursoAnterior(response.numeroConcursoAnterior());
        concurso.setIndicadorConcursoEspecial(response.indicadorConcursoEspecial());
        concurso.setNumeroConcursoFinalEspecial(response.numeroConcursoFinal05());
        concurso.setValorAcumuladoConcurso05(response.valorAcumuladoConcurso05());
        concurso.setValorAcumuladoConcursoEspecial(response.valorAcumuladoConcursoEspecial());
        concurso.setValorSaldoReservaGarantidora(response.valorSaldoReservaGarantidora());
        concurso.setValorTotalPremioFaixaUm(response.valorTotalPremioFaixaUm());
    }

    public void updateEntity(Concurso concurso, CaixaApiResponse response) {
        mapCoreFields(concurso, response);

        concurso.getFaixasPremiacao().clear();
        mapFaixasPremiacao(response, concurso);
        
        concurso.getGanhadoresUF().clear();
        mapGanhadoresUF(response, concurso);
        
        log.info("Concurso {} atualizado com {} faixas e {} ganhadores UF", 
                response.numero(),
                concurso.getFaixasPremiacao().size(),
                concurso.getGanhadoresUF().size());
    }

    private LocalDate parseData(String dataString) {
        if (dataString == null || dataString.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dataString, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Erro ao parsear data '{}': {}", dataString, e.getMessage());
            return null;
        }
    }

    private List<Integer> parseDezenas(List<String> dezenas) {
        if (dezenas == null || dezenas.isEmpty()) {
            return new ArrayList<>();
        }
        return dezenas.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        log.warn("Non-numeric dezena ignored: '{}'", s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void mapFaixasPremiacao(CaixaApiResponse response, Concurso concurso) {
        if (response.listaRateioPremio() == null) {
            return;
        }

        for (RateioPremioDTO rateio : response.listaRateioPremio()) {
            FaixaPremiacao faixa = new FaixaPremiacao(
                    rateio.faixa(),
                    rateio.descricaoFaixa(),
                    rateio.numeroDeGanhadores(),
                    rateio.valorPremio()
            );
            concurso.addFaixaPremiacao(faixa);
        }
    }

    private void mapGanhadoresUF(CaixaApiResponse response, Concurso concurso) {
        if (response.listaMunicipioUFGanhadores() == null || response.listaMunicipioUFGanhadores().isEmpty()) {
            return;
        }

        for (GanhadorDTO ganhador : response.listaMunicipioUFGanhadores()) {
            GanhadorUF ganhadorUF = new GanhadorUF(
                    ganhador.uf(),
                    ganhador.municipio(),
                    ganhador.ganhadores(),
                    1, // Faixa principal
                    ganhador.canal()
            );
            concurso.addGanhadorUF(ganhadorUF);
        }
    }
}
