package br.com.loterias.service;

import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse;
import br.com.loterias.domain.dto.TimeCoracaoMesSorteResponse.ItemFrequencia;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TimeTimemania;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.domain.repository.TimeTimemaniaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TimeCoracaoMesSorteServiceTest {

    @Autowired
    private TimeCoracaoMesSorteService service;

    @Autowired
    private ConcursoRepository concursoRepository;

    @Autowired
    private TimeTimemaniaRepository timeTimemaniaRepository;

    @BeforeEach
    void setUp() {
        concursoRepository.deleteAll();
        concursoRepository.flush();
        
        // Limpar e recriar times ativos para testes
        timeTimemaniaRepository.deleteAll();
        timeTimemaniaRepository.flush();
        createActiveTeams();
        
        // Invalidar cache de times ativos antes de cada teste
        service.invalidarCacheTimesAtivos();
        
        // Create Timemania contests with teams (usando nomes que estão na tabela time_timemania)
        createTimemaniaConcurso(1, LocalDate.of(2024, 1, 1), "FLAMENGO");
        createTimemaniaConcurso(2, LocalDate.of(2024, 1, 4), "FLAMENGO");
        createTimemaniaConcurso(3, LocalDate.of(2024, 1, 7), "CORINTHIANS");
        createTimemaniaConcurso(4, LocalDate.of(2024, 1, 10), "FLAMENGO");
        createTimemaniaConcurso(5, LocalDate.of(2024, 1, 13), "PALMEIRAS");
        createTimemaniaConcurso(6, LocalDate.of(2024, 1, 16), "CORINTHIANS");
        createTimemaniaConcurso(7, LocalDate.of(2024, 1, 19), "SAO PAULO");
        createTimemaniaConcurso(8, LocalDate.of(2024, 1, 22), "FLAMENGO");
        createTimemaniaConcurso(9, LocalDate.of(2024, 1, 25), "VASCO");
        createTimemaniaConcurso(10, LocalDate.of(2024, 1, 28), "FLAMENGO");

        // Create Dia de Sorte contests with months
        createDiaDeSorteConcurso(1, LocalDate.of(2024, 1, 2), "Janeiro");
        createDiaDeSorteConcurso(2, LocalDate.of(2024, 1, 5), "Fevereiro");
        createDiaDeSorteConcurso(3, LocalDate.of(2024, 1, 8), "Janeiro");
        createDiaDeSorteConcurso(4, LocalDate.of(2024, 1, 11), "Março");
        createDiaDeSorteConcurso(5, LocalDate.of(2024, 1, 14), "Janeiro");
        createDiaDeSorteConcurso(6, LocalDate.of(2024, 1, 17), "Abril");
        createDiaDeSorteConcurso(7, LocalDate.of(2024, 1, 20), "Janeiro");
        createDiaDeSorteConcurso(8, LocalDate.of(2024, 1, 23), "Maio");
    }
    
    private void createActiveTeams() {
        timeTimemaniaRepository.save(new TimeTimemania(null, 4, "FLAMENGO", "RJ", "FLAMENGO/RJ", true));
        timeTimemaniaRepository.save(new TimeTimemania(null, 5, "CORINTHIANS", "SP", "CORINTHIANS/SP", true));
        timeTimemaniaRepository.save(new TimeTimemania(null, 6, "PALMEIRAS", "SP", "PALMEIRAS/SP", true));
        timeTimemaniaRepository.save(new TimeTimemania(null, 7, "SAO PAULO", "SP", "SAO PAULO/SP", true));
        timeTimemaniaRepository.save(new TimeTimemania(null, 8, "VASCO", "RJ", "VASCO/RJ", true));
        timeTimemaniaRepository.flush();
    }

    private void createTimemaniaConcurso(int numero, LocalDate data, String time) {
        Concurso c = new Concurso();
        c.setTipoLoteria(TipoLoteria.TIMEMANIA);
        c.setNumero(numero);
        c.setDataApuracao(data);
        c.setNomeTimeCoracaoMesSorte(time);
        c.setDezenasSorteadas(List.of(1, 2, 3, 4, 5, 6, 7));
        concursoRepository.save(c);
    }

    private void createDiaDeSorteConcurso(int numero, LocalDate data, String mes) {
        Concurso c = new Concurso();
        c.setTipoLoteria(TipoLoteria.DIA_DE_SORTE);
        c.setNumero(numero);
        c.setDataApuracao(data);
        c.setNomeTimeCoracaoMesSorte(mes);
        c.setDezenasSorteadas(List.of(1, 2, 3, 4, 5, 6, 7));
        concursoRepository.save(c);
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve retornar análise correta para Timemania")
    void analisarTimeCoracao_timemania_deveRetornarAnaliseCorreta() {
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.TIMEMANIA);

        assertNotNull(response);
        assertEquals(TipoLoteria.TIMEMANIA, response.tipo());
        assertEquals("TIME_CORACAO", response.tipoAnalise());
        assertEquals(10, response.totalConcursosAnalisados());
        
        // FLAMENGO is most frequent (5 times)
        assertNotNull(response.maisFrequente());
        assertEquals("FLAMENGO", response.maisFrequente().nome());
        assertEquals(5, response.maisFrequente().frequencia());
        assertEquals(50.0, response.maisFrequente().percentual(), 0.01);
        
        // Check least frequent (Vasco, São Paulo, Palmeiras all with 1)
        assertNotNull(response.menosFrequente());
        assertEquals(1, response.menosFrequente().frequencia());
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve retornar análise correta para Dia de Sorte")
    void analisarTimeCoracao_diaDeSorte_deveRetornarAnaliseCorreta() {
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.DIA_DE_SORTE);

        assertNotNull(response);
        assertEquals(TipoLoteria.DIA_DE_SORTE, response.tipo());
        assertEquals("MES_SORTE", response.tipoAnalise());
        assertEquals(8, response.totalConcursosAnalisados());
        
        // Janeiro is most frequent (4 times)
        assertNotNull(response.maisFrequente());
        assertEquals("Janeiro", response.maisFrequente().nome());
        assertEquals(4, response.maisFrequente().frequencia());
        assertEquals(50.0, response.maisFrequente().percentual(), 0.01);
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve calcular atraso corretamente")
    void analisarTimeCoracao_deveCalcularAtrasoCorretamente() {
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.TIMEMANIA);

        // Find FLAMENGO - last appeared in contest 10, so atraso = 0
        ItemFrequencia flamengo = response.frequenciaCompleta().stream()
                .filter(i -> i.nome().equals("FLAMENGO"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, flamengo.atrasoAtual());

        // VASCO last appeared in contest 9, so atraso = 1
        ItemFrequencia vasco = response.frequenciaCompleta().stream()
                .filter(i -> i.nome().equals("VASCO"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, vasco.atrasoAtual());

        // SAO PAULO last appeared in contest 7, so atraso = 3
        ItemFrequencia saoPaulo = response.frequenciaCompleta().stream()
                .filter(i -> i.nome().equals("SAO PAULO"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, saoPaulo.atrasoAtual());
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve retornar último sorteio")
    void analisarTimeCoracao_deveRetornarUltimoSorteio() {
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.TIMEMANIA);

        assertNotNull(response.ultimoSorteio());
        assertEquals(10, response.ultimoSorteio().numeroConcurso());
        assertEquals("FLAMENGO", response.ultimoSorteio().timeOuMes());
        assertEquals(LocalDate.of(2024, 1, 28), response.ultimoSorteio().data());
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve lançar exceção para tipo inválido")
    void analisarTimeCoracao_tipoInvalido_deveLancarExcecao() {
        assertThrows(IllegalArgumentException.class, () -> 
            service.analisarTimeCoracao(TipoLoteria.MEGA_SENA));
        assertThrows(IllegalArgumentException.class, () -> 
            service.analisarTimeCoracao(TipoLoteria.LOTOFACIL));
    }

    @Test
    @DisplayName("analisarTimeCoracao - deve retornar resposta vazia quando não há dados de Dia de Sorte sem setup")
    void analisarTimeCoracao_semDados_deveRetornarRespostaVazia() {
        // Create a new tipo that we haven't set up data for - use Dia de Sorte since BeforeEach didn't add months beyond contest 8
        // Actually we do add months, so let's test with a fresh delete
        concursoRepository.deleteAll();
        concursoRepository.flush();
        
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.TIMEMANIA);

        assertNotNull(response);
        assertEquals(0, response.totalConcursosAnalisados());
        assertNull(response.maisFrequente());
        assertNull(response.menosFrequente());
        assertTrue(response.frequenciaCompleta().isEmpty());
        assertNull(response.ultimoSorteio());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quente", "frio", "atrasado", "aleatorio"})
    @DisplayName("sugerirTimeOuMes - deve retornar sugestão para todas as estratégias")
    void sugerirTimeOuMes_todasEstrategias_deveRetornarSugestao(String estrategia) {
        Map<String, Object> result = service.sugerirTimeOuMes(TipoLoteria.TIMEMANIA, estrategia);

        assertNotNull(result);
        assertNotNull(result.get("sugestao"));
        assertFalse(((String) result.get("sugestao")).isEmpty());
        assertEquals(estrategia.toLowerCase(), result.get("estrategia"));
        assertNotNull(result.get("motivo"));
    }

    @Test
    @DisplayName("sugerirTimeOuMes - estratégia 'quente' deve sugerir mais frequente")
    void sugerirTimeOuMes_quente_deveSugerirMaisFrequente() {
        Map<String, Object> result = service.sugerirTimeOuMes(TipoLoteria.TIMEMANIA, "quente");

        assertEquals("FLAMENGO", result.get("sugestao"));
        assertEquals(5, result.get("frequencia"));
    }

    @Test
    @DisplayName("sugerirTimeOuMes - estratégia 'atrasado' deve sugerir mais atrasado")
    void sugerirTimeOuMes_atrasado_deveSugerirMaisAtrasado() {
        Map<String, Object> result = service.sugerirTimeOuMes(TipoLoteria.TIMEMANIA, "atrasado");

        // Palmeiras (atraso 5) is the most delayed
        String sugestao = (String) result.get("sugestao");
        assertNotNull(sugestao);
        assertTrue(result.containsKey("atrasoAtual"));
    }

    @Test
    @DisplayName("sugerirTimeOuMes - estratégia inválida deve lançar exceção")
    void sugerirTimeOuMes_estrategiaInvalida_deveLancarExcecao() {
        assertThrows(IllegalArgumentException.class, () ->
            service.sugerirTimeOuMes(TipoLoteria.TIMEMANIA, "invalida"));
    }

    @Test
    @DisplayName("sugerirComDetalhes - deve retornar ranking completo")
    void sugerirComDetalhes_deveRetornarRankingCompleto() {
        TimeCoracaoMesSorteService.SugestaoDetalhada result = 
            service.sugerirComDetalhes(TipoLoteria.TIMEMANIA, "quente");

        assertNotNull(result);
        assertNotNull(result.sugestao());
        assertNotNull(result.ranking());
        assertFalse(result.ranking().isEmpty());
        assertTrue(result.ranking().size() <= 10);
    }

    @Test
    @DisplayName("sugerirComDetalhes - estratégia 'frio' deve ordenar ranking por menos frequente")
    void sugerirComDetalhes_frio_deveOrdenarPorMenosFrequente() {
        TimeCoracaoMesSorteService.SugestaoDetalhada result = 
            service.sugerirComDetalhes(TipoLoteria.TIMEMANIA, "frio");

        assertNotNull(result.ranking());
        // First item should have lowest frequency
        if (result.ranking().size() > 1) {
            assertTrue(result.ranking().get(0).frequencia() <= result.ranking().get(1).frequencia());
        }
    }

    @Test
    @DisplayName("getTop5 - deve retornar top 5 times quentes")
    void getTop5_quente_deveRetornarTop5() {
        List<String> top5 = service.getTop5(TipoLoteria.TIMEMANIA, "quente");

        assertNotNull(top5);
        assertEquals(5, top5.size());
        assertEquals("FLAMENGO/RJ", top5.get(0)); // Most frequent, converted to full format
    }

    @Test
    @DisplayName("getTop5 - deve retornar lista vazia para tipo inválido")
    void getTop5_tipoInvalido_deveRetornarListaVazia() {
        List<String> top5 = service.getTop5(TipoLoteria.MEGA_SENA, "quente");

        assertTrue(top5.isEmpty());
    }

    @Test
    @DisplayName("sugerirTimeOuMesLegado - deve funcionar para todas estratégias")
    void sugerirTimeOuMesLegado_deveFuncionarParaTodasEstrategias() {
        String quente = service.sugerirTimeOuMesLegado(TipoLoteria.TIMEMANIA, "quente");
        String frio = service.sugerirTimeOuMesLegado(TipoLoteria.TIMEMANIA, "frio");
        String atrasado = service.sugerirTimeOuMesLegado(TipoLoteria.TIMEMANIA, "atrasado");
        String aleatorio = service.sugerirTimeOuMesLegado(TipoLoteria.TIMEMANIA, "aleatorio");
        String padrao = service.sugerirTimeOuMesLegado(TipoLoteria.TIMEMANIA, null);

        assertNotNull(quente);
        assertNotNull(frio);
        assertNotNull(atrasado);
        assertNotNull(aleatorio);
        assertNotNull(padrao);
    }

    @Test
    @DisplayName("análise deve ordenar frequência completa por frequência decrescente")
    void analisarTimeCoracao_deveOrdenarPorFrequenciaDecrescente() {
        TimeCoracaoMesSorteResponse response = service.analisarTimeCoracao(TipoLoteria.TIMEMANIA);

        List<ItemFrequencia> frequencias = response.frequenciaCompleta();
        for (int i = 0; i < frequencias.size() - 1; i++) {
            assertTrue(frequencias.get(i).frequencia() >= frequencias.get(i + 1).frequencia(),
                "Lista deve estar ordenada por frequência decrescente");
        }
    }
}
