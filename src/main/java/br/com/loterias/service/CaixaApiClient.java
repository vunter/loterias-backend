package br.com.loterias.service;

import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.dto.CaixaApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaixaApiClient {

    private static final Logger log = LoggerFactory.getLogger(CaixaApiClient.class);

    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 30000;

    private final RestClient restClient;

    public CaixaApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${loterias.caixa.api.base-url:https://servicebus2.caixa.gov.br/portaldeloterias/api/}") String baseUrl,
            @Value("${caixa.api.timeout:30}") int timeoutSeconds) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
                    setReadTimeout(Duration.ofSeconds(timeoutSeconds));
                }})
                .build();
    }

    public Optional<CaixaApiResponse> buscarConcurso(TipoLoteria tipo, Integer numero) {
        String endpoint = tipo.getEndpoint() + "/" + numero;
        return executarComRetry(endpoint, tipo.getNome(), "concurso " + numero);
    }

    public Optional<CaixaApiResponse> buscarUltimoConcurso(TipoLoteria tipo) {
        String endpoint = tipo.getEndpoint();
        return executarComRetry(endpoint, tipo.getNome(), "último concurso");
    }

    private Optional<CaixaApiResponse> executarComRetry(String endpoint, String tipoNome, String descricao) {
        int tentativa = 0;
        long delay = BASE_DELAY_MS;

        while (tentativa < MAX_RETRIES) {
            tentativa++;
            
            try {
                RateLimitAwareResponse result = fazerRequisicao(endpoint, tipoNome, descricao);
                
                if (result.success()) {
                    return result.response();
                }
                
                if (result.rateLimited()) {
                    long retryAfter = result.retryAfterMs() > 0 ? result.retryAfterMs() : delay;
                    long jitter = ThreadLocalRandom.current().nextLong(100, 500);
                    long sleepTime = Math.min(retryAfter + jitter, MAX_DELAY_MS);
                    
                    log.warn("Rate limit (429) ao buscar {} de {}. Tentativa {}/{}. Aguardando {}ms...",
                            descricao, tipoNome, tentativa, MAX_RETRIES, sleepTime);
                    
                    sleep(sleepTime);
                    delay = Math.min(delay * 2, MAX_DELAY_MS);
                    continue;
                }
                
                return result.response();
                
            } catch (Exception e) {
                if (tentativa < MAX_RETRIES && isRetryableError(e)) {
                    long jitter = ThreadLocalRandom.current().nextLong(100, 500);
                    long sleepTime = Math.min(delay + jitter, MAX_DELAY_MS);
                    
                    log.warn("Erro ao buscar {} de {}. Tentativa {}/{}. Aguardando {}ms. Erro: {}",
                            descricao, tipoNome, tentativa, MAX_RETRIES, sleepTime, e.getMessage());
                    
                    sleep(sleepTime);
                    delay = Math.min(delay * 2, MAX_DELAY_MS);
                } else {
                    log.error("Erro definitivo ao buscar {} de {}: {}", descricao, tipoNome, e.getMessage());
                    return Optional.empty();
                }
            }
        }

        log.error("Máximo de tentativas ({}) excedido ao buscar {} de {}", MAX_RETRIES, descricao, tipoNome);
        return Optional.empty();
    }

    private RateLimitAwareResponse fazerRequisicao(String endpoint, String tipoNome, String descricao) {
        final boolean[] rateLimited = {false};
        final long[] retryAfterMs = {0};

        try {
            CaixaApiResponse response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, clientResponse) -> {
                        rateLimited[0] = true;
                        String retryAfterHeader = clientResponse.getHeaders().getFirst("Retry-After");
                        if (retryAfterHeader != null) {
                            try {
                                retryAfterMs[0] = Long.parseLong(retryAfterHeader) * 1000;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                        if (clientResponse.getStatusCode().value() != 429) {
                            log.warn("{} de {} não encontrado (status: {})",
                                    descricao, tipoNome, clientResponse.getStatusCode());
                        }
                    })
                    .body(CaixaApiResponse.class);

            if (rateLimited[0]) {
                return new RateLimitAwareResponse(false, true, retryAfterMs[0], Optional.empty());
            }

            if (response != null && response.numero() != null) {
                log.debug("{} de {} obtido com sucesso", descricao, tipoNome);
                return new RateLimitAwareResponse(true, false, 0, Optional.of(response));
            }
            
            return new RateLimitAwareResponse(true, false, 0, Optional.empty());

        } catch (Exception e) {
            if (rateLimited[0]) {
                return new RateLimitAwareResponse(false, true, retryAfterMs[0], Optional.empty());
            }
            throw e;
        }
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e instanceof java.net.SocketTimeoutException ||
                   e.getCause() instanceof java.net.SocketTimeoutException;
        }
        
        return message.contains("429") ||
               message.contains("Too Many Requests") ||
               message.contains("Connection reset") ||
               message.contains("Connection refused") ||
               message.contains("Read timed out") ||
               message.contains("connect timed out") ||
               message.contains("ReadTimeoutException") ||
               message.contains("I/O error") ||
               message.contains("timeout") ||
               message.contains("Timeout");
    }

    // Thread.sleep is acceptable here — virtual threads are enabled (spring.threads.virtual.enabled),
    // so sleep parks the virtual thread without blocking the carrier OS thread.
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RateLimitAwareResponse(
            boolean success,
            boolean rateLimited,
            long retryAfterMs,
            Optional<CaixaApiResponse> response
    ) {}
}
