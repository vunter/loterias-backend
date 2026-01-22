package br.com.loterias.service.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shared cache of pre-compiled regex patterns for matching acertos in faixa descriptions.
 */
public final class AcertosPatternCache {

    private static final Map<Integer, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private AcertosPatternCache() {}

    public static Pattern get(int acertos) {
        return PATTERNS.computeIfAbsent(acertos,
                a -> Pattern.compile("(?i).*\\b" + a + "\\b.*"));
    }
}
