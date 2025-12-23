package br.com.loterias.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "concurso", uniqueConstraints = {
    @UniqueConstraint(name = "uk_concurso_tipo_numero", columnNames = {"tipo_loteria", "numero"})
}, indexes = {
    @Index(name = "idx_tipo_loteria", columnList = "tipo_loteria"),
    @Index(name = "idx_tipo_numero", columnList = "tipo_loteria, numero"),
    @Index(name = "idx_data_apuracao", columnList = "data_apuracao"),
    @Index(name = "idx_tipo_data", columnList = "tipo_loteria, data_apuracao")
})
public class Concurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_loteria", nullable = false)
    private TipoLoteria tipoLoteria;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "data_apuracao")
    private LocalDate dataApuracao;

    @ElementCollection
    @CollectionTable(name = "concurso_dezenas", joinColumns = @JoinColumn(name = "concurso_id"))
    @Column(name = "dezena")
    @OrderColumn(name = "posicao")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<Integer> dezenasSorteadas = new ArrayList<>();

    private Boolean acumulado;

    @Column(name = "valor_arrecadado", precision = 19, scale = 2)
    private BigDecimal valorArrecadado;

    @Column(name = "valor_acumulado_proximo_concurso", precision = 19, scale = 2)
    private BigDecimal valorAcumuladoProximoConcurso;

    @Column(name = "local_sorteio")
    private String localSorteio;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ElementCollection
    @CollectionTable(name = "concurso_dezenas_ordem_sorteio", joinColumns = @JoinColumn(name = "concurso_id"))
    @Column(name = "dezena")
    @OrderColumn(name = "posicao")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<Integer> dezenasSorteadasOrdemSorteio = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "concurso_dezenas_segundo_sorteio", joinColumns = @JoinColumn(name = "concurso_id"))
    @Column(name = "dezena")
    @OrderColumn(name = "posicao")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<Integer> dezenasSegundoSorteio = new ArrayList<>();

    @Column(name = "nome_municipio_uf_sorteio", columnDefinition = "TEXT")
    private String nomeMunicipioUFSorteio;

    @Column(name = "nome_time_coracao_mes_sorte")
    private String nomeTimeCoracaoMesSorte;

    @Column(name = "data_proximo_concurso")
    private LocalDate dataProximoConcurso;

    @Column(name = "numero_concurso_proximo")
    private Integer numeroConcursoProximo;

    @Column(name = "numero_concurso_anterior")
    private Integer numeroConcursoAnterior;

    @Column(name = "indicador_concurso_especial")
    private Integer indicadorConcursoEspecial;

    @Column(name = "numero_concurso_final_especial")
    private Integer numeroConcursoFinalEspecial;

    @Column(name = "valor_acumulado_concurso_especial", precision = 19, scale = 2)
    private BigDecimal valorAcumuladoConcursoEspecial;

    @Column(name = "valor_acumulado_concurso_0_5", precision = 19, scale = 2)
    private BigDecimal valorAcumuladoConcurso05;

    @Column(name = "valor_estimado_proximo_concurso", precision = 19, scale = 2)
    private BigDecimal valorEstimadoProximoConcurso;

    @Column(name = "valor_saldo_reserva_garantidora", precision = 19, scale = 2)
    private BigDecimal valorSaldoReservaGarantidora;

    @Column(name = "valor_total_premio_faixa_um", precision = 19, scale = 2)
    private BigDecimal valorTotalPremioFaixaUm;

    @OneToMany(mappedBy = "concurso", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 50)
    private Set<FaixaPremiacao> faixasPremiacao = new HashSet<>();

    @OneToMany(mappedBy = "concurso", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 50)
    private Set<GanhadorUF> ganhadoresUF = new HashSet<>();

    public Concurso() {
    }

    public Concurso(Long id, TipoLoteria tipoLoteria, Integer numero, LocalDate dataApuracao,
                    List<Integer> dezenasSorteadas, Boolean acumulado, BigDecimal valorArrecadado,
                    BigDecimal valorAcumuladoProximoConcurso, String localSorteio, String observacao,
                    Set<FaixaPremiacao> faixasPremiacao, Set<GanhadorUF> ganhadoresUF) {
        this.id = id;
        this.tipoLoteria = tipoLoteria;
        this.numero = numero;
        this.dataApuracao = dataApuracao;
        this.dezenasSorteadas = dezenasSorteadas != null ? dezenasSorteadas : new ArrayList<>();
        this.acumulado = acumulado;
        this.valorArrecadado = valorArrecadado;
        this.valorAcumuladoProximoConcurso = valorAcumuladoProximoConcurso;
        this.localSorteio = localSorteio;
        this.observacao = observacao;
        this.faixasPremiacao = faixasPremiacao != null ? faixasPremiacao : new HashSet<>();
        this.ganhadoresUF = ganhadoresUF != null ? ganhadoresUF : new HashSet<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoLoteria getTipoLoteria() {
        return tipoLoteria;
    }

    public void setTipoLoteria(TipoLoteria tipoLoteria) {
        this.tipoLoteria = tipoLoteria;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public LocalDate getDataApuracao() {
        return dataApuracao;
    }

    public void setDataApuracao(LocalDate dataApuracao) {
        this.dataApuracao = dataApuracao;
    }

    public List<Integer> getDezenasSorteadas() {
        return dezenasSorteadas;
    }

    public void setDezenasSorteadas(List<Integer> dezenasSorteadas) {
        this.dezenasSorteadas = dezenasSorteadas;
    }

    public Boolean getAcumulado() {
        return acumulado;
    }

    public void setAcumulado(Boolean acumulado) {
        this.acumulado = acumulado;
    }

    public BigDecimal getValorArrecadado() {
        return valorArrecadado;
    }

    public void setValorArrecadado(BigDecimal valorArrecadado) {
        this.valorArrecadado = valorArrecadado;
    }

    public BigDecimal getValorAcumuladoProximoConcurso() {
        return valorAcumuladoProximoConcurso;
    }

    public void setValorAcumuladoProximoConcurso(BigDecimal valorAcumuladoProximoConcurso) {
        this.valorAcumuladoProximoConcurso = valorAcumuladoProximoConcurso;
    }

    public String getLocalSorteio() {
        return localSorteio;
    }

    public void setLocalSorteio(String localSorteio) {
        this.localSorteio = localSorteio;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<Integer> getDezenasSorteadasOrdemSorteio() {
        return dezenasSorteadasOrdemSorteio;
    }

    public void setDezenasSorteadasOrdemSorteio(List<Integer> dezenasSorteadasOrdemSorteio) {
        this.dezenasSorteadasOrdemSorteio = dezenasSorteadasOrdemSorteio;
    }

    public List<Integer> getDezenasSegundoSorteio() {
        return dezenasSegundoSorteio;
    }

    public void setDezenasSegundoSorteio(List<Integer> dezenasSegundoSorteio) {
        this.dezenasSegundoSorteio = dezenasSegundoSorteio;
    }

    public String getNomeMunicipioUFSorteio() {
        return nomeMunicipioUFSorteio;
    }

    public void setNomeMunicipioUFSorteio(String nomeMunicipioUFSorteio) {
        this.nomeMunicipioUFSorteio = nomeMunicipioUFSorteio;
    }

    public String getNomeTimeCoracaoMesSorte() {
        return nomeTimeCoracaoMesSorte;
    }

    public void setNomeTimeCoracaoMesSorte(String nomeTimeCoracaoMesSorte) {
        this.nomeTimeCoracaoMesSorte = nomeTimeCoracaoMesSorte;
    }

    public LocalDate getDataProximoConcurso() {
        return dataProximoConcurso;
    }

    public void setDataProximoConcurso(LocalDate dataProximoConcurso) {
        this.dataProximoConcurso = dataProximoConcurso;
    }

    public Integer getNumeroConcursoProximo() {
        return numeroConcursoProximo;
    }

    public void setNumeroConcursoProximo(Integer numeroConcursoProximo) {
        this.numeroConcursoProximo = numeroConcursoProximo;
    }

    public Integer getNumeroConcursoAnterior() {
        return numeroConcursoAnterior;
    }

    public void setNumeroConcursoAnterior(Integer numeroConcursoAnterior) {
        this.numeroConcursoAnterior = numeroConcursoAnterior;
    }

    public Integer getIndicadorConcursoEspecial() {
        return indicadorConcursoEspecial;
    }

    public void setIndicadorConcursoEspecial(Integer indicadorConcursoEspecial) {
        this.indicadorConcursoEspecial = indicadorConcursoEspecial;
    }

    public Integer getNumeroConcursoFinalEspecial() {
        return numeroConcursoFinalEspecial;
    }

    public void setNumeroConcursoFinalEspecial(Integer numeroConcursoFinalEspecial) {
        this.numeroConcursoFinalEspecial = numeroConcursoFinalEspecial;
    }

    public BigDecimal getValorAcumuladoConcursoEspecial() {
        return valorAcumuladoConcursoEspecial;
    }

    public void setValorAcumuladoConcursoEspecial(BigDecimal valorAcumuladoConcursoEspecial) {
        this.valorAcumuladoConcursoEspecial = valorAcumuladoConcursoEspecial;
    }

    public BigDecimal getValorAcumuladoConcurso05() {
        return valorAcumuladoConcurso05;
    }

    public void setValorAcumuladoConcurso05(BigDecimal valorAcumuladoConcurso05) {
        this.valorAcumuladoConcurso05 = valorAcumuladoConcurso05;
    }

    public BigDecimal getValorEstimadoProximoConcurso() {
        return valorEstimadoProximoConcurso;
    }

    public void setValorEstimadoProximoConcurso(BigDecimal valorEstimadoProximoConcurso) {
        this.valorEstimadoProximoConcurso = valorEstimadoProximoConcurso;
    }

    public BigDecimal getValorSaldoReservaGarantidora() {
        return valorSaldoReservaGarantidora;
    }

    public void setValorSaldoReservaGarantidora(BigDecimal valorSaldoReservaGarantidora) {
        this.valorSaldoReservaGarantidora = valorSaldoReservaGarantidora;
    }

    public BigDecimal getValorTotalPremioFaixaUm() {
        return valorTotalPremioFaixaUm;
    }

    public void setValorTotalPremioFaixaUm(BigDecimal valorTotalPremioFaixaUm) {
        this.valorTotalPremioFaixaUm = valorTotalPremioFaixaUm;
    }

    public Set<FaixaPremiacao> getFaixasPremiacao() {
        return faixasPremiacao;
    }

    public void setFaixasPremiacao(Set<FaixaPremiacao> faixasPremiacao) {
        this.faixasPremiacao = faixasPremiacao;
    }

    public Set<GanhadorUF> getGanhadoresUF() {
        return ganhadoresUF;
    }

    public void setGanhadoresUF(Set<GanhadorUF> ganhadoresUF) {
        this.ganhadoresUF = ganhadoresUF;
    }

    public void addFaixaPremiacao(FaixaPremiacao faixa) {
        faixasPremiacao.add(faixa);
        faixa.setConcurso(this);
    }

    public void addGanhadorUF(GanhadorUF ganhador) {
        ganhadoresUF.add(ganhador);
        ganhador.setConcurso(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Concurso concurso = (Concurso) o;
        // Use business key (tipoLoteria + numero) for reliable equality before and after persist
        return Objects.equals(tipoLoteria, concurso.tipoLoteria) && Objects.equals(numero, concurso.numero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoLoteria, numero);
    }

    @Override
    public String toString() {
        return "Concurso{" +
                "id=" + id +
                '}';
    }
}
