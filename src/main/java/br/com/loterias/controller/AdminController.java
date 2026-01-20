package br.com.loterias.controller;

import br.com.loterias.config.CacheConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final CacheConfig cacheConfig;

    public AdminController(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @DeleteMapping("/caches")
    @Operation(summary = "Clear all application caches")
    public Mono<Map<String, String>> clearAllCaches() {
        log.info("Admin: clearing all caches");
        cacheConfig.evictAllCachesNow();
        return Mono.just(Map.of("status", "ok", "message", "All caches cleared"));
    }
}
