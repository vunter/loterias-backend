package br.com.loterias.service;

import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.entity.TipoLoteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaixaApiClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private CaixaApiClient caixaApiClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());

        caixaApiClient = new CaixaApiClient(restClientBuilder, "https://servicebus2.caixa.gov.br/portaldeloterias/api/");
    }

    private CaixaApiResponse buildMockResponse(int numero) {
        return new CaixaApiResponse(
                numero, "01/01/2025",
                List.of("01", "02", "03", "04", "05", "06"),
                List.of("03", "01", "06", "02", "05", "04"),
                null, false,
                new BigDecimal("100000000.00"),
                new BigDecimal("50000000.00"),
                new BigDecimal("60000000.00"),
                "Espaço da Sorte", "São Paulo, SP",
                null, null, "04/01/2025",
                numero + 1, numero - 1, 0, null,
                null, null, null, null,
                List.of(), List.of(), "MEGA_SENA"
        );
    }

    @Nested
    @DisplayName("buscarConcurso")
    class BuscarConcurso {

        @Test
        @DisplayName("deve retornar Optional com resposta quando API retorna dados válidos")
        void deveRetornarRespostaValida() {
            CaixaApiResponse mockResponse = buildMockResponse(3000);
            when(responseSpec.body(CaixaApiResponse.class)).thenReturn(mockResponse);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 3000);

            assertThat(result).isPresent();
            assertThat(result.get().numero()).isEqualTo(3000);
        }

        @Test
        @DisplayName("deve retornar Optional.empty quando API retorna null")
        void deveRetornarVazioQuandoNull() {
            when(responseSpec.body(CaixaApiResponse.class)).thenReturn(null);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 9999);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional.empty quando resposta tem numero null")
        void deveRetornarVazioQuandoNumeroNull() {
            CaixaApiResponse nullNumero = new CaixaApiResponse(
                    null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null
            );
            when(responseSpec.body(CaixaApiResponse.class)).thenReturn(nullNumero);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 1);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional.empty após exceção não recuperável")
        void deveRetornarVazioAposErroNaoRecuperavel() {
            when(responseSpec.body(CaixaApiResponse.class))
                    .thenThrow(new RuntimeException("404 Not Found"));

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 1);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarUltimoConcurso")
    class BuscarUltimoConcurso {

        @Test
        @DisplayName("deve retornar último concurso quando API retorna dados válidos")
        void deveRetornarUltimoConcurso() {
            CaixaApiResponse mockResponse = buildMockResponse(3050);
            when(responseSpec.body(CaixaApiResponse.class)).thenReturn(mockResponse);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarUltimoConcurso(TipoLoteria.LOTOFACIL);

            assertThat(result).isPresent();
            assertThat(result.get().numero()).isEqualTo(3050);
        }

        @Test
        @DisplayName("deve retornar vazio quando API falha")
        void deveRetornarVazioQuandoFalha() {
            when(responseSpec.body(CaixaApiResponse.class))
                    .thenThrow(new RuntimeException("Server unavailable"));

            Optional<CaixaApiResponse> result = caixaApiClient.buscarUltimoConcurso(TipoLoteria.QUINA);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("retry on retryable errors")
    class RetryBehavior {

        @Test
        @DisplayName("deve fazer retry em erros de timeout e eventualmente retornar resposta")
        void deveRetriarEmTimeout() {
            CaixaApiResponse mockResponse = buildMockResponse(100);
            when(responseSpec.body(CaixaApiResponse.class))
                    .thenThrow(new RuntimeException("Read timed out"))
                    .thenReturn(mockResponse);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 100);

            assertThat(result).isPresent();
            assertThat(result.get().numero()).isEqualTo(100);
        }

        @Test
        @DisplayName("deve fazer retry em Connection reset")
        void deveRetriarEmConnectionReset() {
            CaixaApiResponse mockResponse = buildMockResponse(200);
            when(responseSpec.body(CaixaApiResponse.class))
                    .thenThrow(new RuntimeException("Connection reset"))
                    .thenReturn(mockResponse);

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 200);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("deve retornar vazio após esgotar retries")
        @org.junit.jupiter.api.Tag("slow")
        @org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable(named = "CI", matches = "true")
        void deveRetornarVazioAposEsgotarRetries() {
            when(responseSpec.body(CaixaApiResponse.class))
                    .thenThrow(new RuntimeException("Connection refused"));

            Optional<CaixaApiResponse> result = caixaApiClient.buscarConcurso(TipoLoteria.MEGA_SENA, 1);

            assertThat(result).isEmpty();
            verify(restClient, atLeast(2)).get();
        }
    }
}
