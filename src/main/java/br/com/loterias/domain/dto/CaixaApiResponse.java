package br.com.loterias.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CaixaApiResponse(
    Integer numero,
    String dataApuracao,
    List<String> listaDezenas,
    List<String> dezenasSorteadasOrdemSorteio,
    @JsonProperty("listaDezenasSegundoSorteio")
    List<String> listaDezenasSegundoSorteio,
    Boolean acumulado,
    BigDecimal valorArrecadado,
    BigDecimal valorAcumuladoProximoConcurso,
    BigDecimal valorEstimadoProximoConcurso,
    String localSorteio,
    String nomeMunicipioUFSorteio,
    String nomeTimeCoracaoMesSorte,
    String observacao,
    String dataProximoConcurso,
    Integer numeroConcursoProximo,
    Integer numeroConcursoAnterior,
    Integer indicadorConcursoEspecial,
    @JsonProperty("numeroConcursoFinal_0_5")
    Integer numeroConcursoFinal05,
    @JsonProperty("valorAcumuladoConcurso_0_5")
    BigDecimal valorAcumuladoConcurso05,
    BigDecimal valorAcumuladoConcursoEspecial,
    BigDecimal valorSaldoReservaGarantidora,
    BigDecimal valorTotalPremioFaixaUm,
    @JsonProperty("listaRateioPremio")
    List<RateioPremioDTO> listaRateioPremio,
    @JsonProperty("listaMunicipioUFGanhadores")
    List<GanhadorDTO> listaMunicipioUFGanhadores,
    String tipoJogo
) {}
