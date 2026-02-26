package br.com.loterias.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "faixa_premiacao")
public class FaixaPremiacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concurso_id", nullable = false)
    @JsonIgnore
    private Concurso concurso;

    @Column(nullable = false)
    private Integer faixa;

    @Column(name = "descricao_faixa")
    private String descricaoFaixa;

    @Column(name = "numero_ganhadores")
    private Integer numeroGanhadores;

    @Column(name = "valor_premio", precision = 19, scale = 2)
    private BigDecimal valorPremio;

    public FaixaPremiacao() {
    }

    public FaixaPremiacao(Integer faixa, String descricaoFaixa, Integer numeroGanhadores, BigDecimal valorPremio) {
        this.faixa = faixa;
        this.descricaoFaixa = descricaoFaixa;
        this.numeroGanhadores = numeroGanhadores;
        this.valorPremio = valorPremio;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Concurso getConcurso() {
        return concurso;
    }

    public void setConcurso(Concurso concurso) {
        this.concurso = concurso;
    }

    public Integer getFaixa() {
        return faixa;
    }

    public void setFaixa(Integer faixa) {
        this.faixa = faixa;
    }

    public String getDescricaoFaixa() {
        return descricaoFaixa;
    }

    public void setDescricaoFaixa(String descricaoFaixa) {
        this.descricaoFaixa = descricaoFaixa;
    }

    public Integer getNumeroGanhadores() {
        return numeroGanhadores;
    }

    public void setNumeroGanhadores(Integer numeroGanhadores) {
        this.numeroGanhadores = numeroGanhadores;
    }

    public BigDecimal getValorPremio() {
        return valorPremio;
    }

    public void setValorPremio(BigDecimal valorPremio) {
        this.valorPremio = valorPremio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FaixaPremiacao that = (FaixaPremiacao) o;
        return Objects.equals(faixa, that.faixa)
                && Objects.equals(concurso, that.concurso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(faixa, concurso);
    }
}
