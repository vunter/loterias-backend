package br.com.loterias.controller;

import br.com.loterias.domain.entity.TipoLoteria;

import java.util.Arrays;

/**
 * Shared utility for parsing TipoLoteria from path variables.
 */
public final class TipoLoteriaParser {

    private TipoLoteriaParser() {}

    public static TipoLoteria parse(String tipo) {
        try {
            return TipoLoteria.valueOf(tipo.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Tipo de loteria '%s' inválido. Tipos válidos: %s",
                            tipo, Arrays.toString(TipoLoteria.values())));
        }
    }
}
