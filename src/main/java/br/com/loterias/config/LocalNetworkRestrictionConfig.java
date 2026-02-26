package br.com.loterias.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

/**
 * Restricts destructive (DELETE, POST to import/fix) endpoints to local network only.
 */
@Configuration
public class LocalNetworkRestrictionConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalNetworkRestrictionConfig.class);

    private static final Set<String> RESTRICTED_PATH_PREFIXES = Set.of(
            "/api/import/",
            "/api/admin/"
    );

    @Value("${loterias.security.allowed-networks:127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1}")
    private List<String> allowedNetworks;

    @Value("${ADMIN_API_KEY:}")
    private String adminApiKey;

    @Bean
    @Order(1)
    public WebFilter localNetworkFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            HttpMethod method = request.getMethod();

            boolean isRestricted = false;

            if (method == HttpMethod.DELETE && (path.startsWith("/api/import/") || path.startsWith("/api/admin/"))) {
                isRestricted = true;
            }
            if (method == HttpMethod.POST && RESTRICTED_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
                isRestricted = true;
            }
            // Sync endpoints are admin-only
            if (path.contains("/sync")) {
                isRestricted = true;
            }

            if (isRestricted) {
                // Check API key first (allows remote authenticated access)
                String apiKey = request.getHeaders().getFirst("X-Admin-Api-Key");
                boolean hasValidApiKey = adminApiKey != null && !adminApiKey.isBlank()
                        && apiKey != null && adminApiKey.equals(apiKey);

                if (!hasValidApiKey && !isLocalNetwork(exchange)) {
                    log.warn("Blocked restricted endpoint access from remote IP: {} -> {} {}",
                            getRemoteAddress(exchange), method, path);
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }

            return chain.filter(exchange);
        };
    }

    private boolean isLocalNetwork(ServerWebExchange exchange) {
        InetSocketAddress remoteAddr = exchange.getRequest().getRemoteAddress();
        if (remoteAddr == null) return false;

        InetAddress addr = remoteAddr.getAddress();
        if (addr == null) return false;

        boolean directIsLocal = addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || isAllowedAddress(addr.getHostAddress());

        if (directIsLocal) {
            // Only trust X-Forwarded-For when direct connection is from a local/trusted proxy
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String clientIp = forwarded.split(",")[0].trim();
                return isAllowedAddress(clientIp);
            }
            return true;
        }

        return false;
    }

    private boolean isAllowedAddress(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                return true;
            }
        } catch (Exception ignored) {}

        for (String network : allowedNetworks) {
            if (matchesCidr(ip, network)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return ip.equals(cidr);
            }
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLen = Integer.parseInt(parts[1]);

            byte[] addr = InetAddress.getByName(ip).getAddress();
            byte[] net = network.getAddress();

            if (addr.length != net.length) return false;

            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != net[i]) return false;
            }
            if (remainingBits > 0 && fullBytes < addr.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addr[fullBytes] & mask) != (net[fullBytes] & mask)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getRemoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddr = exchange.getRequest().getRemoteAddress();
        if (remoteAddr == null) return "unknown";
        InetAddress addr = remoteAddr.getAddress();
        return addr != null ? addr.getHostAddress() : remoteAddr.getHostString();
    }
}
