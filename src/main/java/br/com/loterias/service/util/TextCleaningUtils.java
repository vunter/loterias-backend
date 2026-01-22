package br.com.loterias.service.util;

/**
 * Shared text-cleaning utilities for lottery data.
 */
public final class TextCleaningUtils {

    private TextCleaningUtils() {}

    /**
     * Cleans the nomeTimeCoracaoMesSorte field by removing null characters and trimming whitespace.
     */
    public static String cleanNomeTimeCoracao(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\u0000", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
