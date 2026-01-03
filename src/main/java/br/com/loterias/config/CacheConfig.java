package br.com.loterias.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String CACHE_ESTATISTICAS = "estatisticas";
    public static final String CACHE_TIME_CORACAO = "timeCoracao";
    public static final String CACHE_ESPECIAIS = "especiais";
    public static final String CACHE_DASHBOARD = "dashboard";
    public static final String CACHE_FINANCEIRO = "financeiro";

    @Value("${loterias.cache.maximum-size:500}")
    private int maximumSize;

    private final int expireAfterWriteMinutes;

    private volatile CaffeineCacheManager caffeineCacheManager;

    public CacheConfig(LoteriaProperties properties,
                       @Value("${loterias.cache.expire-after-write-minutes:60}") int fallbackMinutes) {
        if (properties != null && properties.cache() != null && properties.cache().expireAfterWriteMinutes() > 0) {
            this.expireAfterWriteMinutes = properties.cache().expireAfterWriteMinutes();
        } else {
            this.expireAfterWriteMinutes = fallbackMinutes;
        }
    }

    @Bean
    public CacheManager cacheManager() {
        caffeineCacheManager = new CaffeineCacheManager(
                CACHE_ESTATISTICAS, CACHE_TIME_CORACAO, CACHE_ESPECIAIS, CACHE_DASHBOARD, CACHE_FINANCEIRO);
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
                .recordStats());
        return caffeineCacheManager;
    }

    public void evictCache(String cacheName) {
        if (caffeineCacheManager != null) {
            var cache = caffeineCacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    public void evictAllCachesNow() {
        if (caffeineCacheManager != null) {
            caffeineCacheManager.getCacheNames()
                    .forEach(name -> {
                        var cache = caffeineCacheManager.getCache(name);
                        if (cache != null) cache.clear();
                    });
        }
    }
}
