package br.com.loterias.service;

import br.com.loterias.domain.entity.TipoLoteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Shared validation utilities for game generation services.
 */
public final class GeradorValidation {

    private static final Logger log = LoggerFactory.getLogger(GeradorValidation.class);

    private GeradorValidation() {}

    /**
     * Clamps the requested number count to the lottery's [min, max] range.
     */
    public static int validarQuantidadeNumeros(TipoLoteria tipo, Integer quantidadeNumeros) {
        if (quantidadeNumeros == null) {
            return tipo.getMinimoNumeros();
        }
        int resultado = Math.max(tipo.getMinimoNumeros(), Math.min(quantidadeNumeros, tipo.getMaximoNumeros()));
        if (resultado != quantidadeNumeros) {
            log.debug("Quantidade de números ajustada: solicitado={}, ajustado={}, tipo={}", quantidadeNumeros, resultado, tipo.getNome());
        }
        return resultado;
    }

    /**
     * Validates trevo count for +Milionária, accounting for fixed trevos.
     */
    public static int validarQuantidadeTrevos(Integer quantidadeTrevos, List<Integer> trevosFixos) {
        int minTrevos = 2;
        int maxTrevos = 6;

        int minFromFixos = (trevosFixos != null) ? trevosFixos.size() : 0;
        minTrevos = Math.max(minTrevos, minFromFixos);

        if (quantidadeTrevos == null) {
            return Math.max(2, minFromFixos);
        }
        int resultado = Math.max(minTrevos, Math.min(quantidadeTrevos, maxTrevos));
        if (resultado != quantidadeTrevos) {
            log.debug("Quantidade de trevos ajustada: solicitado={}, ajustado={}, fixos={}", quantidadeTrevos, resultado, minFromFixos);
        }
        return resultado;
    }
}
