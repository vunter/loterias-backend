package br.com.loterias.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estratégias de geração de jogos baseadas em estatísticas")
public enum EstrategiaGeracao {
    
    @Schema(description = "Geração aleatória sem análise estatística")
    ALEATORIO("Aleatório", "Números gerados aleatoriamente sem análise estatística"),
    
    @Schema(description = "Prioriza números que mais saíram historicamente")
    NUMEROS_QUENTES("Números Quentes", "Prioriza os números que mais saíram no histórico"),
    
    @Schema(description = "Prioriza números que menos saíram historicamente")
    NUMEROS_FRIOS("Números Frios", "Prioriza os números que menos saíram no histórico"),
    
    @Schema(description = "Prioriza números que estão há mais tempo sem sair")
    NUMEROS_ATRASADOS("Números Atrasados", "Prioriza números que estão há mais concursos sem sair"),
    
    @Schema(description = "Usa números que mais saíram em concursos COM ganhadores")
    NUMEROS_PREMIADOS("Números Premiados", "Usa números frequentes em concursos que tiveram ganhador na faixa principal"),
    
    @Schema(description = "Equilibra entre números quentes e frios")
    EQUILIBRADO("Equilibrado", "Combina metade de números quentes e metade de números frios"),
    
    @Schema(description = "Baseado nos pares de números que mais saem juntos")
    PARES_FREQUENTES("Pares Frequentes", "Usa pares de números que mais aparecem juntos nos sorteios"),
    
    @Schema(description = "Distribuição equilibrada entre faixas numéricas")
    DISTRIBUICAO_FAIXAS("Distribuição por Faixas", "Garante números distribuídos em todas as faixas (01-10, 11-20, etc)"),
    
    @Schema(description = "Combina múltiplas estratégias estatísticas")
    COMBINADO("Combinado", "Combina números quentes, atrasados e balanceamento par/ímpar"),
    
    @Schema(description = "Baseado nos últimos N concursos apenas")
    TENDENCIA_RECENTE("Tendência Recente", "Analisa apenas os últimos 50 concursos para identificar tendências");

    private final String nome;
    private final String descricao;

    EstrategiaGeracao(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }
}
