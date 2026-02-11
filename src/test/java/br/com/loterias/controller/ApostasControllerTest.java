package br.com.loterias.controller;

import br.com.loterias.domain.dto.ResumoVerificacao;
import br.com.loterias.domain.dto.VerificarApostaRequest;
import br.com.loterias.domain.dto.VerificarApostaResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.VerificadorApostasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = {ApostasController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = CacheAutoConfiguration.class)
@Import(br.com.loterias.config.TestCacheConfig.class)
class ApostasControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private VerificadorApostasService verificadorApostasService;

    @Nested
    @DisplayName("POST /api/apostas/{tipo}/verificar")
    class VerificarAposta {

        @Test
        @DisplayName("deve retornar 200 com resultado da verificação")
        void deveRetornar200ComResultado() {
            VerificarApostaResponse mockResponse = new VerificarApostaResponse(
                    List.of(4, 8, 15, 16, 23, 42),
                    List.of(),
                    new ResumoVerificacao(50, 2, 0, 1, new BigDecimal("150.00"))
            );
            when(verificadorApostasService.verificarAposta(eq(TipoLoteria.MEGA_SENA), any(VerificarApostaRequest.class)))
                    .thenReturn(mockResponse);

            webTestClient.post()
                    .uri("/api/apostas/mega_sena/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"numeros\": [4, 8, 15, 16, 23, 42], \"concursoInicio\": 2700, \"concursoFim\": 2750}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.numerosApostados").isArray()
                    .jsonPath("$.resumo.totalConcursosVerificados").isEqualTo(50)
                    .jsonPath("$.resumo.valorTotalPremios").isEqualTo(150.00);

            verify(verificadorApostasService).verificarAposta(eq(TipoLoteria.MEGA_SENA), any(VerificarApostaRequest.class));
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.post()
                    .uri("/api/apostas/loteria_invalida/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"numeros\": [1, 2, 3, 4, 5, 6], \"concursoInicio\": 1, \"concursoFim\": 10}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(400)
                    .jsonPath("$.message").exists();
        }

        @Test
        @DisplayName("deve retornar 400 para body com numeros vazio")
        void deveRetornar400ParaNumerosVazio() {
            webTestClient.post()
                    .uri("/api/apostas/mega_sena/verificar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"numeros\": [], \"concursoInicio\": 1, \"concursoFim\": 10}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
