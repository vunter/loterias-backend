package br.com.loterias.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory rate limiter using a fixed window counter per IP.
 * Limits requests per IP to prevent abuse/DoS. Actuator and health endpoints are excluded.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    private static final int MAX_TRACKED_IPS = 10_000;

    @Value("${loterias.rate-limit.requests-per-minute:120}")
    private int requestsPerMinute;

    @Value("${loterias.rate-limit.enabled:true}")
    private boolean enabled;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    private record WindowCounter(AtomicLong count, AtomicLong windowStart) {
        WindowCounter() {
            this(new AtomicLong(0), new AtomicLong(System.currentTimeMillis()));
        }

        boolean tryAcquire(int maxRequests, long windowMillis) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start > windowMillis) {
                if (windowStart.compareAndSet(start, now)) {
                    count.set(1);
                    return true;
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }

    @Bean
    WebFilter rateLimitFilter() {
        long windowMillis = Duration.ofMinutes(1).toMillis();

        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            counters.entrySet().removeIf(e ->
                    now - e.getValue().windowStart().get() > windowMillis * 2);
        }, 5, 5, TimeUnit.MINUTES);

        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            if (!enabled) {
                return chain.filter(exchange);
            }

            String path = exchange.getRequest().getPath().value();
            if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
                return chain.filter(exchange);
            }

            // Cap tracked IPs to prevent memory exhaustion
            if (counters.size() >= MAX_TRACKED_IPS) {
                long now = System.currentTimeMillis();
                counters.entrySet().removeIf(e ->
                        now - e.getValue().windowStart().get() > windowMillis);
            }

            String clientIp = resolveClientIp(exchange);
            var counter = counters.computeIfAbsent(clientIp, k -> new WindowCounter());

            if (!counter.tryAcquire(requestsPerMinute, windowMillis)) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().set("Retry-After", "60");
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    @PreDestroy
    void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        InetAddress address = remoteAddress.getAddress();
        return address != null ? address.getHostAddress() : remoteAddress.getHostString();
    }
}
