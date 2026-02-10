package br.com.loterias.service;

import br.com.loterias.domain.dto.VerificarApostaRequest;
import br.com.loterias.domain.dto.VerificarApostaResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificadorApostasServiceTest {

    @Mock
    private ConcursoRepository concursoRepository;

    @InjectMocks
    private VerificadorApostasService verificadorService;

    private Concurso concurso1;
    private Concurso concurso2;

    @BeforeEach
    void setUp() {
        concurso1 = new Concurso();
        concurso1.setTipoLoteria(TipoLoteria.LOTOFACIL);
        concurso1.setNumero(3001);
        concurso1.setDataApuracao(LocalDate.of(2024, 1, 15));
        concurso1.setDezenasSorteadas(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        concurso1.setAcumulado(false);

        FaixaPremiacao faixa15 = new FaixaPremiacao();
        faixa15.setFaixa(1);
        faixa15.setDescricaoFaixa("15 acertos");
        faixa15.setNumeroGanhadores(2);
        faixa15.setValorPremio(new BigDecimal("500000.00"));
        concurso1.addFaixaPremiacao(faixa15);

        FaixaPremiacao faixa14 = new FaixaPremiacao();
        faixa14.setFaixa(2);
        faixa14.setDescricaoFaixa("14 acertos");
        faixa14.setNumeroGanhadores(150);
        faixa14.setValorPremio(new BigDecimal("1500.00"));
        concurso1.addFaixaPremiacao(faixa14);

        FaixaPremiacao faixa11 = new FaixaPremiacao();
        faixa11.setFaixa(5);
        faixa11.setDescricaoFaixa("11 acertos");
        faixa11.setNumeroGanhadores(200000);
        faixa11.setValorPremio(new BigDecimal("6.00"));
        concurso1.addFaixaPremiacao(faixa11);

        concurso2 = new Concurso();
        concurso2.setTipoLoteria(TipoLoteria.LOTOFACIL);
        concurso2.setNumero(3002);
        concurso2.setDataApuracao(LocalDate.of(2024, 1, 16));
        concurso2.setDezenasSorteadas(List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19));
        concurso2.setAcumulado(true);

        FaixaPremiacao faixa2_14 = new FaixaPremiacao();
        faixa2_14.setFaixa(2);
        faixa2_14.setDescricaoFaixa("14 acertos");
        faixa2_14.setNumeroGanhadores(100);
        faixa2_14.setValorPremio(new BigDecimal("2000.00"));
        concurso2.addFaixaPremiacao(faixa2_14);
    }

    @Test
    void verificarAposta_deveRetornarResultadosParaDoisConcursos() {
        when(concursoRepository.findByTipoLoteriaAndNumeroRange(TipoLoteria.LOTOFACIL, 3001, 3002))
            .thenReturn(List.of(concurso1, concurso2));

        VerificarApostaRequest request = new VerificarApostaRequest(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 3001, 3002);

        VerificarApostaResponse response = verificadorService.verificarAposta(TipoLoteria.LOTOFACIL, request);

        assertNotNull(response);
        assertEquals(2, response.resultados().size());
        assertEquals(15, response.numerosApostados().size());
        assertNotNull(response.resumo());
    }

    @Test
    void verificarAposta_deveCalcularAcertosCorretamente() {
        when(concursoRepository.findByTipoLoteriaAndNumeroRange(TipoLoteria.LOTOFACIL, 3001, 3001))
            .thenReturn(List.of(concurso1));

        VerificarApostaRequest request = new VerificarApostaRequest(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 3001, 3001);

        VerificarApostaResponse response = verificadorService.verificarAposta(TipoLoteria.LOTOFACIL, request);

        assertEquals(1, response.resultados().size());
        assertEquals(15, response.resultados().getFirst().quantidadeAcertos());
        assertEquals("15 acertos", response.resultados().getFirst().faixaPremiacao());
        assertEquals(new BigDecimal("500000.00"), response.resultados().getFirst().valorPremio());
    }

    @Test
    void verificarAposta_deveCalcularAcertosParciais() {
        when(concursoRepository.findByTipoLoteriaAndNumeroRange(TipoLoteria.LOTOFACIL, 3002, 3002))
            .thenReturn(List.of(concurso2));

        VerificarApostaRequest request = new VerificarApostaRequest(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 3002, 3002);

        VerificarApostaResponse response = verificadorService.verificarAposta(TipoLoteria.LOTOFACIL, request);

        assertEquals(1, response.resultados().size());
        assertEquals(11, response.resultados().getFirst().quantidadeAcertos());
    }

    @Test
    void verificarAposta_deveSomarPremios() {
        when(concursoRepository.findByTipoLoteriaAndNumeroRange(TipoLoteria.LOTOFACIL, 3001, 3002))
            .thenReturn(List.of(concurso1, concurso2));

        VerificarApostaRequest request = new VerificarApostaRequest(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 3001, 3002);

        VerificarApostaResponse response = verificadorService.verificarAposta(TipoLoteria.LOTOFACIL, request);

        assertTrue(response.resumo().valorTotalPremios().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(response.resumo().totalPremiacoes() >= 1);
    }
}
