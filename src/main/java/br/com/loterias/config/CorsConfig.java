package br.com.loterias.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

@Configuration
@EnableConfigurationProperties(LoteriaProperties.class)
public class CorsConfig {

    /** Matches RFC-1918 private IPs: 10.x.x.x, 172.16-31.x.x, 192.168.x.x */
    private static final Pattern LAN_ORIGIN = Pattern.compile(
            "^https?://(localhost|127\\.\\d+\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+)(:\\d+)?$");

    private final List<String> allowedOrigins;

    public CorsConfig(LoteriaProperties properties,
                      @Value("${loterias.cors.allowed-origins:#{null}}") List<String> fallbackOrigins) {
        if (properties != null && properties.cors() != null && properties.cors().allowedOrigins() != null) {
            this.allowedOrigins = properties.cors().allowedOrigins();
        } else if (fallbackOrigins != null) {
            this.allowedOrigins = fallbackOrigins;
        } else {
            this.allowedOrigins = List.of("http://localhost:3000", "http://localhost:3001",
                    "http://127.0.0.1:3000", "http://127.0.0.1:3001");
        }
    }

    private boolean isOriginAllowed(String origin) {
        if (origin == null) return false;
        if (allowedOrigins.contains(origin)) return true;
        if (origin.startsWith("chrome-extension://")) return true;
        return LAN_ORIGIN.matcher(origin).matches();
    }

    @Bean
    public WebFilter corsFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            String origin = request.getHeaders().getOrigin();
            if (origin != null && isOriginAllowed(origin)) {
                headers.add("Access-Control-Allow-Origin", origin);
                headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                headers.add("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With, Authorization");
                headers.add("Access-Control-Max-Age", "3600");
                headers.add("Vary", "Origin");

                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            } else if (request.getMethod() == HttpMethod.OPTIONS) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return Mono.empty();
            }

            return chain.filter(exchange);
        };
    }
}
