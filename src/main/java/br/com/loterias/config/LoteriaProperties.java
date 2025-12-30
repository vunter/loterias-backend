package br.com.loterias.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "loterias")
public record LoteriaProperties(
    CorsProperties cors,
    CaixaProperties caixa,
    CacheProperties cache
) {
    public record CorsProperties(List<String> allowedOrigins) {}
    public record CaixaProperties(ApiProperties api) {
        public record ApiProperties(String baseUrl) {}
    }
    public record CacheProperties(int expireAfterWriteMinutes) {}
}
