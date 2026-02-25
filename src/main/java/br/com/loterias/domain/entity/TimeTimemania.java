package br.com.loterias.domain.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "time_timemania")
public class TimeTimemania {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_caixa", nullable = false, unique = true)
    private Integer codigoCaixa;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(name = "nome_completo", nullable = false, unique = true)
    private String nomeCompleto;

    private Boolean ativo;

    public TimeTimemania() {
    }

    public TimeTimemania(Long id, Integer codigoCaixa, String nome, String uf, String nomeCompleto, Boolean ativo) {
        this.id = id;
        this.codigoCaixa = codigoCaixa;
        this.nome = nome;
        this.uf = uf;
        this.nomeCompleto = nomeCompleto;
        this.ativo = ativo;
    }

    public Integer getCodigoCaixa() {
        return codigoCaixa;
    }

    public void setCodigoCaixa(Integer codigoCaixa) {
        this.codigoCaixa = codigoCaixa;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeTimemania that = (TimeTimemania) o;
        return Objects.equals(codigoCaixa, that.codigoCaixa);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codigoCaixa);
    }

    @Override
    public String toString() {
        return "TimeTimemania{" +
                "id=" + id +
                ", nomeCompleto='" + nomeCompleto + '\'' +
                '}';
    }
}
