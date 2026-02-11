package br.com.loterias.controller;

import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.service.AtualizarGanhadoresService;
import br.com.loterias.service.ConcursoSyncService;
import br.com.loterias.service.SyncRateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = {ConcursoController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = CacheAutoConfiguration.class)
@Import(br.com.loterias.config.TestCacheConfig.class)
class ConcursoControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ConcursoRepository concursoRepository;

    @MockitoBean
    private ConcursoSyncService concursoSyncService;

    @MockitoBean
    private AtualizarGanhadoresService atualizarGanhadoresService;

    @MockitoBean
    private SyncRateLimitService syncRateLimitService;

    private Concurso buildMockConcurso(TipoLoteria tipo, int numero) {
        Concurso c = new Concurso();
        c.setId((long) numero);
        c.setTipoLoteria(tipo);
        c.setNumero(numero);
        c.setDataApuracao(LocalDate.of(2024, 1, 15));
        c.setDezenasSorteadas(List.of(5, 12, 23, 34, 45, 56));
        c.setAcumulado(false);
        c.setValorArrecadado(new BigDecimal("100000000.00"));
        c.setValorAcumuladoProximoConcurso(BigDecimal.ZERO);
        return c;
    }

    @Nested
    @DisplayName("GET /api/concursos/{tipo}")
    class ListarConcursos {

        @Test
        @DisplayName("deve retornar 200 com resultados paginados")
        void deveRetornar200ComResultadosPaginados() {
            Concurso concurso = buildMockConcurso(TipoLoteria.MEGA_SENA, 2800);
            PageImpl<Concurso> page = new PageImpl<>(List.of(concurso), PageRequest.of(0, 20), 1);
            when(concursoRepository.findByTipoLoteriaPaged(eq(TipoLoteria.MEGA_SENA), any()))
                    .thenReturn(page);

            webTestClient.get()
                    .uri("/api/concursos/mega_sena")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].numero").isEqualTo(2800)
                    .jsonPath("$.content[0].tipoLoteria").isEqualTo("MEGA_SENA")
                    .jsonPath("$.totalElements").isEqualTo(1);

            verify(concursoRepository).findByTipoLoteriaPaged(eq(TipoLoteria.MEGA_SENA), any());
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.get()
                    .uri("/api/concursos/loteria_invalida")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(400)
                    .jsonPath("$.message").exists();
        }
    }

    @Nested
    @DisplayName("GET /api/concursos/{tipo}/{numero}")
    class BuscarConcurso {

        @Test
        @DisplayName("deve retornar 200 com concurso específico")
        void deveRetornar200ComConcursoEspecifico() {
            Concurso concurso = buildMockConcurso(TipoLoteria.MEGA_SENA, 2800);
            when(concursoRepository.findByTipoLoteriaAndNumero(TipoLoteria.MEGA_SENA, 2800))
                    .thenReturn(Optional.of(concurso));

            webTestClient.get()
                    .uri("/api/concursos/mega_sena/2800")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.numero").isEqualTo(2800)
                    .jsonPath("$.tipoLoteria").isEqualTo("MEGA_SENA")
                    .jsonPath("$.dezenasSorteadas").isArray();

            verify(concursoRepository).findByTipoLoteriaAndNumero(TipoLoteria.MEGA_SENA, 2800);
        }

        @Test
        @DisplayName("deve retornar 404 quando concurso não encontrado")
        void deveRetornar404QuandoNaoEncontrado() {
            when(concursoRepository.findByTipoLoteriaAndNumero(TipoLoteria.MEGA_SENA, 9999))
                    .thenReturn(Optional.empty());

            webTestClient.get()
                    .uri("/api/concursos/mega_sena/9999")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(404)
                    .jsonPath("$.message").exists();
        }
    }

    @Nested
    @DisplayName("POST /api/concursos/sync-all")
    class SyncAll {

        @Test
        @DisplayName("deve retornar 200 com resultados de sincronização")
        void deveRetornar200ComResultadosSincronizacao() {
            Map<String, Integer> resultados = new LinkedHashMap<>();
            resultados.put("Mega-Sena", 5);
            resultados.put("Lotofácil", 3);
            when(concursoSyncService.sincronizarTodosNovosConcursos()).thenReturn(resultados);

            webTestClient.post()
                    .uri("/api/concursos/sync-all")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalSincronizados").isEqualTo(8)
                    .jsonPath("$.resultados").exists();

            verify(concursoSyncService).sincronizarTodosNovosConcursos();
        }
    }
}
