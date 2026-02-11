package br.com.loterias.controller;

import br.com.loterias.domain.dto.AnaliseNumeroResponse;
import br.com.loterias.domain.dto.ConferirApostaResponse;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse;
import br.com.loterias.domain.dto.DashboardResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.AnaliseNumeroService;
import br.com.loterias.service.ConferirApostaService;
import br.com.loterias.service.ConcursosEspeciaisService;
import br.com.loterias.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = {DashboardController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = CacheAutoConfiguration.class)
@Import(br.com.loterias.config.TestCacheConfig.class)
class DashboardControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private ConferirApostaService conferirApostaService;

    @MockitoBean
    private AnaliseNumeroService analiseNumeroService;

    @MockitoBean
    private ConcursosEspeciaisService concursosEspeciaisService;

    private DashboardResponse buildMockDashboard(TipoLoteria tipo) {
        return new DashboardResponse(
                tipo,
                tipo.getNome(),
                new DashboardResponse.ResumoGeral(
                        2800, LocalDate.of(1996, 3, 11), LocalDate.now(),
                        0, new BigDecimal("500000000.00"), 2749, 50000000.0
                ),
                new DashboardResponse.UltimoConcursoInfo(
                        2800, LocalDate.now(),
                        List.of(5, 12, 23, 34, 45, 56),
                        List.of(),
                        true, new BigDecimal("50000000.00"),
                        0, BigDecimal.ZERO, List.of()
                ),
                null,
                List.of(10, 53, 5),
                List.of(26, 55, 9),
                List.of(22, 15, 33),
                new DashboardResponse.AnalisePatterns(
                        3.0, 3.0, 1.5, 183.0,
                        null, 2, 3.0, 3.0
                ),
                new DashboardResponse.ProximoConcursoInfo(
                        2801, LocalDate.now().plusDays(3),
                        new BigDecimal("60000000.00"), true
                ),
                null
        );
    }

    @Nested
    @DisplayName("GET /api/dashboard/{tipo}")
    class GetDashboard {

        @Test
        @DisplayName("deve retornar 200 com dados do dashboard")
        void deveRetornar200() {
            DashboardResponse mockResponse = buildMockDashboard(TipoLoteria.MEGA_SENA);
            when(dashboardService.gerarDashboard(TipoLoteria.MEGA_SENA)).thenReturn(mockResponse);

            webTestClient.get()
                    .uri("/api/dashboard/mega_sena")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.nomeLoteria").isEqualTo("Mega-Sena")
                    .jsonPath("$.resumo.totalConcursos").isEqualTo(2800)
                    .jsonPath("$.ultimoConcurso.numero").isEqualTo(2800);

            verify(dashboardService).gerarDashboard(TipoLoteria.MEGA_SENA);
        }

        @Test
        @DisplayName("deve aceitar tipo com underscores e converter para enum")
        void deveAceitarTipoComUnderscores() {
            DashboardResponse mockResponse = buildMockDashboard(TipoLoteria.DIA_DE_SORTE);
            when(dashboardService.gerarDashboard(TipoLoteria.DIA_DE_SORTE)).thenReturn(mockResponse);

            webTestClient.get()
                    .uri("/api/dashboard/dia_de_sorte")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.nomeLoteria").isEqualTo("Dia de Sorte");
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.get()
                    .uri("/api/dashboard/loteria_invalida")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(400)
                    .jsonPath("$.message").exists();
        }
    }

    @Nested
    @DisplayName("GET /api/dashboard/{tipo}/conferir")
    class ConferirAposta {

        @Test
        @DisplayName("deve retornar 200 com resultado da conferência")
        void deveRetornar200() {
            ConferirApostaResponse mockResponse = new ConferirApostaResponse(
                    TipoLoteria.MEGA_SENA,
                    List.of(4, 8, 15, 16, 23, 42),
                    new ConferirApostaResponse.ResumoConferencia(
                            500, 3, 0.6, new BigDecimal("150.00"), 4, 2500
                    ),
                    List.of()
            );
            when(conferirApostaService.conferirNoHistorico(eq(TipoLoteria.MEGA_SENA), anyList()))
                    .thenReturn(mockResponse);

            webTestClient.get()
                    .uri("/api/dashboard/mega_sena/conferir?numeros=4,8,15,16,23,42")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.resumo.vezesPremiado").isEqualTo(3);
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.get()
                    .uri("/api/dashboard/invalido/conferir?numeros=1,2,3")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/dashboard/especiais")
    class DashboardEspeciais {

        @Test
        @DisplayName("deve retornar 200 com dados dos concursos especiais")
        void deveRetornar200() {
            ConcursosEspeciaisResponse mockResponse = new ConcursosEspeciaisResponse(
                    List.of(), BigDecimal.ZERO, List.of()
            );
            when(concursosEspeciaisService.gerarDashboardEspeciais()).thenReturn(mockResponse);

            webTestClient.get()
                    .uri("/api/dashboard/especiais")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalAcumuladoEspeciais").isEqualTo(0);
        }
    }
}
