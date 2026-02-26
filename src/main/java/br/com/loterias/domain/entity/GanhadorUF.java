package br.com.loterias.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "ganhador_uf")
public class GanhadorUF {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concurso_id", nullable = false)
    @JsonIgnore
    private Concurso concurso;

    @Column(length = 2)
    private String uf;

    private String cidade;

    @Column(name = "numero_ganhadores")
    private Integer numeroGanhadores;

    private Integer faixa;

    private String canal;

    public GanhadorUF() {
    }

    public GanhadorUF(String uf, String cidade, Integer numeroGanhadores, Integer faixa, String canal) {
        this.uf = uf;
        this.cidade = cidade;
        this.numeroGanhadores = numeroGanhadores;
        this.faixa = faixa;
        this.canal = canal;
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

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public Integer getNumeroGanhadores() {
        return numeroGanhadores;
    }

    public void setNumeroGanhadores(Integer numeroGanhadores) {
        this.numeroGanhadores = numeroGanhadores;
    }

    public Integer getFaixa() {
        return faixa;
    }

    public void setFaixa(Integer faixa) {
        this.faixa = faixa;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GanhadorUF that = (GanhadorUF) o;
        return Objects.equals(uf, that.uf)
                && Objects.equals(faixa, that.faixa)
                && Objects.equals(cidade, that.cidade)
                && Objects.equals(concurso, that.concurso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uf, faixa, cidade, concurso);
    }
}
