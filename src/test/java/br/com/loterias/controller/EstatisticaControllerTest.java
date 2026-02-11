package br.com.loterias.controller;

import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.service.EstatisticaService;
import br.com.loterias.service.GeradorEstrategicoService;
import br.com.loterias.service.GeradorJogosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.*;

@WebFluxTest(controllers = {EstatisticaController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = CacheAutoConfiguration.class)
@Import(br.com.loterias.config.TestCacheConfig.class)
class EstatisticaControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EstatisticaService estatisticaService;

    @MockitoBean
    private GeradorJogosService geradorJogosService;

    @MockitoBean
    private GeradorEstrategicoService geradorEstrategicoService;

    @Nested
    @DisplayName("GET /api/estatisticas/{tipo}/frequencia")
    class Frequencia {

        @Test
        @DisplayName("deve retornar 200 com mapa de frequências")
        void deveRetornar200() {
            Map<Integer, Long> mockFrequencia = new TreeMap<>();
            mockFrequencia.put(1, 245L);
            mockFrequencia.put(2, 238L);
            mockFrequencia.put(3, 251L);

            when(estatisticaService.frequenciaTodosNumeros(TipoLoteria.MEGA_SENA))
                    .thenReturn(mockFrequencia);

            webTestClient.get()
                    .uri("/api/estatisticas/mega_sena/frequencia")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.1").isEqualTo(245)
                    .jsonPath("$.2").isEqualTo(238)
                    .jsonPath("$.3").isEqualTo(251);

            verify(estatisticaService).frequenciaTodosNumeros(TipoLoteria.MEGA_SENA);
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.get()
                    .uri("/api/estatisticas/invalido/frequencia")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(400)
                    .jsonPath("$.message").exists();
        }

        @Test
        @DisplayName("deve aceitar diferentes tipos de loteria")
        void deveAceitarDiferentesTipos() {
            when(estatisticaService.frequenciaTodosNumeros(TipoLoteria.LOTOFACIL))
                    .thenReturn(new TreeMap<>());

            webTestClient.get()
                    .uri("/api/estatisticas/lotofacil/frequencia")
                    .exchange()
                    .expectStatus().isOk();

            verify(estatisticaService).frequenciaTodosNumeros(TipoLoteria.LOTOFACIL);
        }
    }

    @Nested
    @DisplayName("GET /api/estatisticas/{tipo}/atrasados")
    class Atrasados {

        @Test
        @DisplayName("deve retornar 200 com mapa de atrasados usando quantidade padrão")
        void deveRetornar200ComQuantidadePadrao() {
            Map<Integer, Long> mockAtrasados = new LinkedHashMap<>();
            mockAtrasados.put(26, 45L);
            mockAtrasados.put(55, 38L);
            mockAtrasados.put(9, 32L);

            when(estatisticaService.numerosAtrasados(TipoLoteria.MEGA_SENA, 10))
                    .thenReturn(mockAtrasados);

            webTestClient.get()
                    .uri("/api/estatisticas/mega_sena/atrasados")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.26").isEqualTo(45)
                    .jsonPath("$.55").isEqualTo(38);

            verify(estatisticaService).numerosAtrasados(TipoLoteria.MEGA_SENA, 10);
        }

        @Test
        @DisplayName("deve aceitar parâmetro quantidade customizado")
        void deveAceitarQuantidadeCustomizada() {
            when(estatisticaService.numerosAtrasados(TipoLoteria.QUINA, 5))
                    .thenReturn(new LinkedHashMap<>());

            webTestClient.get()
                    .uri("/api/estatisticas/quina/atrasados?quantidade=5")
                    .exchange()
                    .expectStatus().isOk();

            verify(estatisticaService).numerosAtrasados(TipoLoteria.QUINA, 5);
        }

        @Test
        @DisplayName("deve retornar 400 para tipo inválido")
        void deveRetornar400ParaTipoInvalido() {
            webTestClient.get()
                    .uri("/api/estatisticas/xyz/atrasados")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/estatisticas/{tipo}/mais-frequentes")
    class MaisFrequentes {

        @Test
        @DisplayName("deve retornar 200 com números mais frequentes")
        void deveRetornar200() {
            Map<Integer, Long> mock = new LinkedHashMap<>();
            mock.put(10, 312L);
            mock.put(53, 298L);

            when(estatisticaService.numerosMaisFrequentes(TipoLoteria.MEGA_SENA, 10))
                    .thenReturn(mock);

            webTestClient.get()
                    .uri("/api/estatisticas/mega_sena/mais-frequentes")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.10").isEqualTo(312)
                    .jsonPath("$.53").isEqualTo(298);
        }
    }

    @Nested
    @DisplayName("GET /api/estatisticas/{tipo}/pares-impares")
    class ParesImpares {

        @Test
        @DisplayName("deve retornar 200 com médias de pares/ímpares")
        void deveRetornar200() {
            Map<String, Double> mock = new LinkedHashMap<>();
            mock.put("mediaPares", 3.12);
            mock.put("mediaImpares", 2.88);

            when(estatisticaService.paresImpares(TipoLoteria.MEGA_SENA))
                    .thenReturn(mock);

            webTestClient.get()
                    .uri("/api/estatisticas/mega_sena/pares-impares")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.mediaPares").isEqualTo(3.12)
                    .jsonPath("$.mediaImpares").isEqualTo(2.88);
        }
    }

    @Nested
    @DisplayName("GET /api/estatisticas/{tipo}/soma-media")
    class SomaMedia {

        @Test
        @DisplayName("deve retornar 200 com soma média")
        void deveRetornar200() {
            when(estatisticaService.somaMedia(TipoLoteria.MEGA_SENA)).thenReturn(183.45);

            webTestClient.get()
                    .uri("/api/estatisticas/mega_sena/soma-media")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isEqualTo(183.45);
        }
    }

    @Nested
    @DisplayName("parseTipoLoteria conversion")
    class ParseTipoLoteria {

        @Test
        @DisplayName("deve converter tipo com hífens para underscores")
        void deveConverterHifenParaUnderscore() {
            when(estatisticaService.frequenciaTodosNumeros(TipoLoteria.MEGA_SENA))
                    .thenReturn(new TreeMap<>());

            webTestClient.get()
                    .uri("/api/estatisticas/mega-sena/frequencia")
                    .exchange()
                    .expectStatus().isOk();

            verify(estatisticaService).frequenciaTodosNumeros(TipoLoteria.MEGA_SENA);
        }

        @Test
        @DisplayName("deve ser case-insensitive")
        void deveFuncionarCaseInsensitive() {
            when(estatisticaService.frequenciaTodosNumeros(TipoLoteria.MEGA_SENA))
                    .thenReturn(new TreeMap<>());

            webTestClient.get()
                    .uri("/api/estatisticas/MEGA_SENA/frequencia")
                    .exchange()
                    .expectStatus().isOk();
        }
    }
}
