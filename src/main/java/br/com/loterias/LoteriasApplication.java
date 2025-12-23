package br.com.loterias;

import br.com.loterias.config.DatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
public class LoteriasApplication {

    private static final Logger log = LoggerFactory.getLogger(LoteriasApplication.class);

    public static void main(String[] args) {
        initDatabase();
        SpringApplication.run(LoteriasApplication.class, args);
    }

    private static void initDatabase() {
        try {
            Properties props = new Properties();
            try (InputStream is = LoteriasApplication.class.getResourceAsStream("/application-db.properties")) {
                if (is != null) {
                    props.load(is);
                }
            }

            String url = resolveProperty(props.getProperty("spring.datasource.url"));
            String username = resolveProperty(props.getProperty("spring.datasource.username"));
            String password = resolveProperty(props.getProperty("spring.datasource.password"));

            if (url != null && url.contains("postgresql")) {
                DatabaseInitializer.createDatabaseIfNotExists(url, username, password);
            }
        } catch (Exception e) {
            log.warn("Could not initialize database: {}", e.getMessage(), e);
        }
    }

    private static String resolveProperty(String value) {
        if (value == null || !value.startsWith("${")) return value;
        // Parse ${ENV_VAR:default}
        String inner = value.substring(2, value.length() - 1);
        int colonIdx = inner.indexOf(':');
        String envVar = colonIdx > 0 ? inner.substring(0, colonIdx) : inner;
        String defaultVal = colonIdx > 0 ? inner.substring(colonIdx + 1) : "";
        String envValue = System.getenv(envVar);
        return envValue != null ? envValue : defaultVal;
    }
}
