package br.com.loterias.domain.dto;

public record GanhadorDTO(
    Integer ganhadores,
    String municipio,
    String uf,
    String canal
) {}
