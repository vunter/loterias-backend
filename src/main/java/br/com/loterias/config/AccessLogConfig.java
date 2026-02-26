package br.com.loterias.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.net.InetSocketAddress;

@Configuration
public class AccessLogConfig {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS_LOG");

    @Bean
    @Order(0)
    public WebFilter accessLogFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            long startTime = System.nanoTime();
            String method = exchange.getRequest().getMethod().name();
            String path = exchange.getRequest().getPath().value();
            String remoteIp = getRemoteIp(exchange);

            return chain.filter(exchange).doFinally(signal -> {
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                int status = exchange.getResponse().getStatusCode() != null
                        ? exchange.getResponse().getStatusCode().value()
                        : 0;

                accessLog.info("{} {} {} {}ms {}", method, path, status, durationMs, remoteIp);
            });
        };
    }

    private String getRemoteIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr != null && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
