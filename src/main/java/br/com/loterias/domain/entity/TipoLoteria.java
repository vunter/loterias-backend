package br.com.loterias.domain.entity;

public enum TipoLoteria {

    MEGA_SENA("Mega-Sena", 1, 60, 6, 20, "megasena", "Mega-Sena", 6),
    LOTOFACIL("Lotofácil", 1, 25, 15, 20, "lotofacil", "Lotofácil", 15),
    QUINA("Quina", 1, 80, 5, 15, "quina", "Quina", 5),
    LOTOMANIA("Lotomania", 0, 99, 50, 50, "lotomania", "Lotomania", 20),
    TIMEMANIA("Timemania", 1, 80, 10, 10, "timemania", "Timemania", 7),
    DUPLA_SENA("Dupla Sena", 1, 50, 6, 15, "duplasena", "Dupla-Sena", 6),
    DIA_DE_SORTE("Dia de Sorte", 1, 31, 7, 15, "diadesorte", "Dia de Sorte", 7),
    SUPER_SETE("Super Sete", 0, 9, 7, 21, "supersete", "Super Sete", 7),
    MAIS_MILIONARIA("+Milionária", 1, 50, 6, 12, "maismilionaria", "+Milionária", 6);

    private final String nome;
    private final int numeroInicial;
    private final int numeroFinal;
    private final int minimoNumeros;
    private final int maximoNumeros;
    private final String endpoint;
    private final String modalidadeDownload;
    private final int quantidadeBolas;

    TipoLoteria(String nome, int numeroInicial, int numeroFinal, int minimoNumeros, int maximoNumeros, 
                String endpoint, String modalidadeDownload, int quantidadeBolas) {
        this.nome = nome;
        this.numeroInicial = numeroInicial;
        this.numeroFinal = numeroFinal;
        this.minimoNumeros = minimoNumeros;
        this.maximoNumeros = maximoNumeros;
        this.endpoint = endpoint;
        this.modalidadeDownload = modalidadeDownload;
        this.quantidadeBolas = quantidadeBolas;
    }

    public String getNome() {
        return nome;
    }

    public int getNumeroInicial() {
        return numeroInicial;
    }

    public int getNumeroFinal() {
        return numeroFinal;
    }

    public int getNumerosDezenas() {
        return numeroFinal - numeroInicial + 1;
    }

    public int getMinimoNumeros() {
        return minimoNumeros;
    }

    public int getMaximoNumeros() {
        return maximoNumeros;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getModalidadeDownload() {
        return modalidadeDownload;
    }

    public int getQuantidadeBolas() {
        return quantidadeBolas;
    }
}
