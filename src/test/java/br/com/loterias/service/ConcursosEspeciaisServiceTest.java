package br.com.loterias.service;

import br.com.loterias.domain.dto.ConcursosEspeciaisResponse;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse.LoteriaEspecialInfo;
import br.com.loterias.domain.dto.ConcursosEspeciaisResponse.ProximoConcursoEspecialInfo;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ConcursosEspeciaisServiceTest {

    @Autowired
    private ConcursosEspeciaisService service;

    @Autowired
    private ConcursoRepository concursoRepository;

    @BeforeEach
    void setUp() {
        concursoRepository.deleteAll();
        concursoRepository.flush();
    }

    private Concurso createConcurso(TipoLoteria tipo, int numero, LocalDate data, BigDecimal valorEspecial) {
        Concurso c = new Concurso();
        c.setTipoLoteria(tipo);
        c.setNumero(numero);
        c.setDataApuracao(data);
        c.setDezenasSorteadas(List.of(1, 2, 3, 4, 5, 6));
        c.setValorAcumuladoConcursoEspecial(valorEspecial);
        c.setIndicadorConcursoEspecial(valorEspecial != null && valorEspecial.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        c.setNumeroConcursoFinalEspecial(numero + 50);
        return concursoRepository.save(c);
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve retornar dashboard vazio quando não há concursos")
    void gerarDashboardEspeciais_semConcursos_deveRetornarDashboardVazio() {
        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        assertNotNull(response);
        assertTrue(response.loteriasComEspecial().isEmpty());
        assertEquals(BigDecimal.ZERO, response.totalAcumuladoEspeciais());
        assertTrue(response.proximosConcursosEspeciais().isEmpty());
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve incluir Mega-Sena com acumulado especial")
    void gerarDashboardEspeciais_megaSena_deveIncluirAcumuladoEspecial() {
        BigDecimal valorVirada = new BigDecimal("500000000.00");
        createConcurso(TipoLoteria.MEGA_SENA, 2700, LocalDate.now().minusDays(3), valorVirada);

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        assertNotNull(response);
        assertFalse(response.loteriasComEspecial().isEmpty());
        
        Optional<LoteriaEspecialInfo> megaSena = response.loteriasComEspecial().stream()
                .filter(l -> l.tipo().equals("MEGA_SENA"))
                .findFirst();
        
        assertTrue(megaSena.isPresent());
        assertEquals("Mega da Virada", megaSena.get().nomeEspecial());
        assertEquals(valorVirada, megaSena.get().valorAcumuladoConcursoEspecial());
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve calcular total acumulado corretamente")
    void gerarDashboardEspeciais_deveCalcularTotalAcumulado() {
        createConcurso(TipoLoteria.MEGA_SENA, 2700, LocalDate.now().minusDays(3), new BigDecimal("100000000.00"));
        createConcurso(TipoLoteria.LOTOFACIL, 3100, LocalDate.now().minusDays(2), new BigDecimal("200000000.00"));
        createConcurso(TipoLoteria.QUINA, 6300, LocalDate.now().minusDays(1), new BigDecimal("50000000.00"));

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        assertEquals(new BigDecimal("350000000.00"), response.totalAcumuladoEspeciais());
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve retornar cores corretas das loterias")
    void gerarDashboardEspeciais_deveRetornarCoresCorretas() {
        createConcurso(TipoLoteria.MEGA_SENA, 2700, LocalDate.now().minusDays(3), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.LOTOFACIL, 3100, LocalDate.now().minusDays(2), new BigDecimal("1000.00"));

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        Optional<LoteriaEspecialInfo> megaSena = response.loteriasComEspecial().stream()
                .filter(l -> l.tipo().equals("MEGA_SENA"))
                .findFirst();
        Optional<LoteriaEspecialInfo> lotofacil = response.loteriasComEspecial().stream()
                .filter(l -> l.tipo().equals("LOTOFACIL"))
                .findFirst();

        assertTrue(megaSena.isPresent());
        assertEquals("#209869", megaSena.get().cor());
        
        assertTrue(lotofacil.isPresent());
        assertEquals("#930089", lotofacil.get().cor());
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve incluir próximos especiais ordenados por data")
    void gerarDashboardEspeciais_deveOrdenarProximosEspeciaisPorData() {
        createConcurso(TipoLoteria.MEGA_SENA, 2700, LocalDate.of(2025, 11, 1), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.QUINA, 6300, LocalDate.of(2025, 5, 1), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.LOTOFACIL, 3100, LocalDate.of(2025, 8, 1), new BigDecimal("1000.00"));

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        List<ProximoConcursoEspecialInfo> proximos = response.proximosConcursosEspeciais();
        if (proximos.size() >= 2) {
            for (int i = 0; i < proximos.size() - 1; i++) {
                if (proximos.get(i).dataEstimada() != null && proximos.get(i + 1).dataEstimada() != null) {
                    assertTrue(
                        proximos.get(i).dataEstimada().compareTo(proximos.get(i + 1).dataEstimada()) <= 0,
                        "Próximos especiais devem estar ordenados por data"
                    );
                }
            }
        }
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve retornar nomes corretos dos especiais")
    void gerarDashboardEspeciais_deveRetornarNomesCorretos() {
        createConcurso(TipoLoteria.MEGA_SENA, 2700, LocalDate.now(), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.LOTOFACIL, 3100, LocalDate.now(), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.QUINA, 6300, LocalDate.now(), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.DUPLA_SENA, 2500, LocalDate.now(), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.LOTOMANIA, 2600, LocalDate.now(), new BigDecimal("1000.00"));
        createConcurso(TipoLoteria.DIA_DE_SORTE, 900, LocalDate.now(), new BigDecimal("1000.00"));

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        response.loteriasComEspecial().forEach(info -> {
            switch (info.tipo()) {
                case "MEGA_SENA" -> assertEquals("Mega da Virada", info.nomeEspecial());
                case "LOTOFACIL" -> assertEquals("Lotofácil da Independência", info.nomeEspecial());
                case "QUINA" -> assertEquals("Quina de São João", info.nomeEspecial());
                case "DUPLA_SENA" -> assertEquals("Dupla de Páscoa", info.nomeEspecial());
                case "LOTOMANIA" -> assertEquals("Lotomania de Páscoa", info.nomeEspecial());
                case "DIA_DE_SORTE" -> assertEquals("Dia de Sorte de São João", info.nomeEspecial());
            }
        });
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - loterias sem especial devem ter nomeEspecial nulo")
    void gerarDashboardEspeciais_loteriaSemEspecial_nomeEspecialNulo() {
        createConcurso(TipoLoteria.SUPER_SETE, 500, LocalDate.now(), null);

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        Optional<LoteriaEspecialInfo> superSete = response.loteriasComEspecial().stream()
                .filter(l -> l.tipo().equals("SUPER_SETE"))
                .findFirst();

        assertTrue(superSete.isPresent());
        assertNull(superSete.get().nomeEspecial());
    }

    @Test
    @DisplayName("calcularPascoa - deve calcular data correta para anos conhecidos")
    void calcularPascoa_deveCalcularDatasCorretas() throws Exception {
        Method calcularPascoa = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularPascoa", int.class);
        calcularPascoa.setAccessible(true);

        // Known Easter dates
        assertEquals(LocalDate.of(2024, 3, 31), calcularPascoa.invoke(service, 2024));
        assertEquals(LocalDate.of(2025, 4, 20), calcularPascoa.invoke(service, 2025));
        assertEquals(LocalDate.of(2026, 4, 5), calcularPascoa.invoke(service, 2026));
        assertEquals(LocalDate.of(2027, 3, 28), calcularPascoa.invoke(service, 2027));
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - deve incluir último concurso info")
    void gerarDashboardEspeciais_deveIncluirUltimoConcursoInfo() {
        LocalDate dataApuracao = LocalDate.of(2024, 12, 15);
        BigDecimal valorArrecadado = new BigDecimal("50000000.00");
        BigDecimal valorEstimado = new BigDecimal("60000000.00");
        
        Concurso c = new Concurso();
        c.setTipoLoteria(TipoLoteria.MEGA_SENA);
        c.setNumero(2750);
        c.setDataApuracao(dataApuracao);
        c.setDezenasSorteadas(List.of(5, 12, 23, 34, 45, 56));
        c.setValorArrecadado(valorArrecadado);
        c.setValorEstimadoProximoConcurso(valorEstimado);
        c.setValorAcumuladoConcursoEspecial(new BigDecimal("500000000.00"));
        c.setLocalSorteio("Espaço da Sorte");
        c.setNomeMunicipioUFSorteio("São Paulo, SP");
        concursoRepository.save(c);

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        Optional<LoteriaEspecialInfo> megaSena = response.loteriasComEspecial().stream()
                .filter(l -> l.tipo().equals("MEGA_SENA"))
                .findFirst();

        assertTrue(megaSena.isPresent());
        assertNotNull(megaSena.get().ultimoConcurso());
        assertEquals(2750, megaSena.get().ultimoConcurso().numero());
        assertEquals(dataApuracao, megaSena.get().ultimoConcurso().data());
        assertEquals(List.of(5, 12, 23, 34, 45, 56), megaSena.get().ultimoConcurso().dezenas());
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - concursos por semana devem variar por loteria")
    void gerarDashboardEspeciais_concursosPorSemanaDiferentes() throws Exception {
        Method getConcursosPorSemana = ConcursosEspeciaisService.class
                .getDeclaredMethod("getConcursosPorSemana", TipoLoteria.class);
        getConcursosPorSemana.setAccessible(true);

        // Lotofácil and Quina: 6x per week (Mon-Sat)
        assertEquals(6.0, getConcursosPorSemana.invoke(service, TipoLoteria.LOTOFACIL));
        assertEquals(6.0, getConcursosPorSemana.invoke(service, TipoLoteria.QUINA));
        
        // Mega-Sena, Timemania, Dia de Sorte: 3x per week
        assertEquals(3.0, getConcursosPorSemana.invoke(service, TipoLoteria.MEGA_SENA));
        assertEquals(3.0, getConcursosPorSemana.invoke(service, TipoLoteria.TIMEMANIA));
        assertEquals(3.0, getConcursosPorSemana.invoke(service, TipoLoteria.DIA_DE_SORTE));
        
        // +Milionária: 2x per week
        assertEquals(2.0, getConcursosPorSemana.invoke(service, TipoLoteria.MAIS_MILIONARIA));
    }

    @Test
    @DisplayName("gerarDashboardEspeciais - não deve incluir concursos especiais passados")
    void gerarDashboardEspeciais_naoDeveIncluirEspeciaisPassados() {
        // Contest with past date and zero accumulated
        Concurso c = new Concurso();
        c.setTipoLoteria(TipoLoteria.MEGA_SENA);
        c.setNumero(2700);
        c.setDataApuracao(LocalDate.now().minusDays(3));
        c.setDezenasSorteadas(List.of(1, 2, 3, 4, 5, 6));
        c.setValorAcumuladoConcursoEspecial(BigDecimal.ZERO);
        concursoRepository.save(c);

        ConcursosEspeciaisResponse response = service.gerarDashboardEspeciais();

        assertTrue(response.proximosConcursosEspeciais().isEmpty() || 
            response.proximosConcursosEspeciais().stream()
                .noneMatch(p -> p.tipoLoteria().equals("MEGA_SENA") && p.valorAcumulado().equals(BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("Mega da Virada deve ser dia 31 de dezembro")
    void calcularDataConcursoEspecial_megaDaVirada_deve31Dezembro() throws Exception {
        Method calcularData = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularDataConcursoEspecial", TipoLoteria.class, Concurso.class);
        calcularData.setAccessible(true);

        Concurso c = new Concurso();
        c.setDataApuracao(LocalDate.of(2025, 6, 1));

        LocalDate result = (LocalDate) calcularData.invoke(service, TipoLoteria.MEGA_SENA, c);

        assertNotNull(result);
        assertEquals(Month.DECEMBER, result.getMonth());
        assertEquals(31, result.getDayOfMonth());
    }

    @Test
    @DisplayName("Quina de São João deve ser próximo ao dia 24 de junho")
    void calcularDataConcursoEspecial_quinaSaoJoao_proximo24Junho() throws Exception {
        Method calcularData = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularDataConcursoEspecial", TipoLoteria.class, Concurso.class);
        calcularData.setAccessible(true);

        Concurso c = new Concurso();
        c.setDataApuracao(LocalDate.of(2025, 1, 15));

        LocalDate result = (LocalDate) calcularData.invoke(service, TipoLoteria.QUINA, c);

        assertNotNull(result);
        assertEquals(Month.JUNE, result.getMonth());
    }

    @Test
    @DisplayName("Lotofácil Independência deve ser próximo ao dia 7 de setembro")
    void calcularDataConcursoEspecial_lotofacilIndependencia_proximo7Setembro() throws Exception {
        Method calcularData = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularDataConcursoEspecial", TipoLoteria.class, Concurso.class);
        calcularData.setAccessible(true);

        Concurso c = new Concurso();
        c.setDataApuracao(LocalDate.of(2025, 1, 15));

        LocalDate result = (LocalDate) calcularData.invoke(service, TipoLoteria.LOTOFACIL, c);

        assertNotNull(result);
        assertEquals(Month.SEPTEMBER, result.getMonth());
    }

    @Test
    @DisplayName("Dupla de Páscoa deve ser sábado de Aleluia (dia antes da Páscoa)")
    void calcularDataConcursoEspecial_duplaPascoa_sabadoAleluia() throws Exception {
        Method calcularData = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularDataConcursoEspecial", TipoLoteria.class, Concurso.class);
        Method calcularPascoa = ConcursosEspeciaisService.class
                .getDeclaredMethod("calcularPascoa", int.class);
        calcularData.setAccessible(true);
        calcularPascoa.setAccessible(true);

        Concurso c = new Concurso();
        c.setDataApuracao(LocalDate.of(2025, 1, 15));

        LocalDate result = (LocalDate) calcularData.invoke(service, TipoLoteria.DUPLA_SENA, c);
        
        assertNotNull(result);
        // Result should be a Saturday (day before Easter for some year)
        // The exact year depends on current date, so we just verify it's 1 day before Easter of the result's year
        LocalDate pascoaResultYear = (LocalDate) calcularPascoa.invoke(service, result.getYear());
        assertEquals(pascoaResultYear.minusDays(1), result, "Sábado de Aleluia is one day before Easter Sunday");
    }
}
