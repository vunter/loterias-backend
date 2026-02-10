package br.com.loterias.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TestCacheConfig {
    @Bean
    @Primary
    CacheManager testCacheManager() {
        return new NoOpCacheManager();
    }
}
