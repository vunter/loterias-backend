package br.com.loterias.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SyncRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(SyncRateLimitService.class);

    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(2);
    
    private final AtomicReference<Instant> lastSyncTime = new AtomicReference<>(Instant.EPOCH);

    public record RateLimitStatus(boolean allowed, long remainingSeconds, Instant lastSync) {}

    public RateLimitStatus checkRateLimit() {
        Instant last = lastSyncTime.get();
        Instant now = Instant.now();
        Duration elapsed = Duration.between(last, now);
        
        if (elapsed.compareTo(COOLDOWN_DURATION) >= 0) {
            log.debug("Rate limit check: allowed, elapsed={}s", elapsed.toSeconds());
            return new RateLimitStatus(true, 0, last);
        }
        
        long remaining = COOLDOWN_DURATION.minus(elapsed).toSeconds();
        log.debug("Rate limit check: denied, remainingSeconds={}", remaining);
        return new RateLimitStatus(false, remaining, last);
    }

    public void recordSync() {
        log.info("Recording sync timestamp");
        lastSyncTime.set(Instant.now());
    }

    public RateLimitStatus getStatus() {
        return checkRateLimit();
    }

    public long getCooldownSeconds() {
        return COOLDOWN_DURATION.toSeconds();
    }
}
