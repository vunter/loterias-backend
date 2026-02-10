package br.com.loterias.service;

import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstatisticaServiceTest {

    @Mock
    private ConcursoRepository concursoRepository;

    @InjectMocks
    private EstatisticaService estatisticaService;

    @Nested
    @DisplayName("frequenciaTodosNumeros")
    class FrequenciaTodosNumeros {

        @Test
        @DisplayName("deve inicializar todos os números com 0 e preencher com resultados do repositório")
        void deveInicializarTodosNumerosEPreencherResultados() {
            List<Object[]> mockResults = List.of(
                    new Object[]{5, 120L},
                    new Object[]{10, 200L},
                    new Object[]{60, 50L}
            );
            when(concursoRepository.findFrequenciaDezenas("MEGA_SENA")).thenReturn(mockResults);

            Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(TipoLoteria.MEGA_SENA);

            // MEGA_SENA: 1..60
            assertThat(frequencia).hasSize(60);
            assertThat(frequencia).containsEntry(5, 120L);
            assertThat(frequencia).containsEntry(10, 200L);
            assertThat(frequencia).containsEntry(60, 50L);
            assertThat(frequencia).containsEntry(1, 0L);
            assertThat(frequencia).containsEntry(30, 0L);
        }

        @Test
        @DisplayName("deve retornar TreeMap ordenado por chave")
        void deveRetornarTreeMapOrdenado() {
            when(concursoRepository.findFrequenciaDezenas("MEGA_SENA")).thenReturn(List.of());

            Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(TipoLoteria.MEGA_SENA);

            assertThat(frequencia).isInstanceOf(TreeMap.class);
            List<Integer> keys = new ArrayList<>(frequencia.keySet());
            assertThat(keys).isSorted();
            assertThat(keys.getFirst()).isEqualTo(1);
            assertThat(keys.getLast()).isEqualTo(60);
        }

        @Test
        @DisplayName("deve funcionar com resultados vazios — todos zerados")
        void deveRetornarTodosZeradosQuandoSemResultados() {
            when(concursoRepository.findFrequenciaDezenas("LOTOFACIL")).thenReturn(List.of());

            Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(TipoLoteria.LOTOFACIL);

            // LOTOFACIL: 1..25
            assertThat(frequencia).hasSize(25);
            assertThat(frequencia.values()).allMatch(v -> v == 0L);
        }

        @Test
        @DisplayName("deve respeitar numeroInicial e numeroFinal do tipo loteria")
        void deveRespeitarLimitesDeTipo() {
            // LOTOMANIA: 0..99
            when(concursoRepository.findFrequenciaDezenas("LOTOMANIA")).thenReturn(List.of());

            Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(TipoLoteria.LOTOMANIA);

            assertThat(frequencia).hasSize(100);
            assertThat(frequencia).containsKey(0);
            assertThat(frequencia).containsKey(99);
        }

        @Test
        @DisplayName("deve lidar com Number subtipos (Integer, BigInteger) do repositório")
        void deveConverterNumberSubtipos() {
            List<Object[]> mockResults = new ArrayList<>();
            mockResults.add(new Object[]{Integer.valueOf(3), Long.valueOf(55L)});
            mockResults.add(new Object[]{Short.valueOf((short) 7), Integer.valueOf(88)});
            when(concursoRepository.findFrequenciaDezenas("QUINA")).thenReturn(mockResults);

            Map<Integer, Long> frequencia = estatisticaService.frequenciaTodosNumeros(TipoLoteria.QUINA);

            // QUINA: 1..80
            assertThat(frequencia).hasSize(80);
            assertThat(frequencia).containsEntry(3, 55L);
            assertThat(frequencia).containsEntry(7, 88L);
        }
    }

    @Nested
    @DisplayName("numerosAtrasados")
    class NumerosAtrasados {

        @Test
        @DisplayName("deve calcular atraso corretamente — atraso = ultimoConcurso - ultimaAparicao")
        void deveCalcularAtrasoCorretamente() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.MEGA_SENA))
                    .thenReturn(Optional.of(3000));

            List<Object[]> ultimaAparicao = List.of(
                    new Object[]{5, 2990},   // atraso = 10
                    new Object[]{10, 2950},  // atraso = 50
                    new Object[]{15, 3000}   // atraso = 0
            );
            when(concursoRepository.findUltimaAparicaoDezenas("MEGA_SENA")).thenReturn(ultimaAparicao);

            // Request top 60 to get all numbers
            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.MEGA_SENA, 60);

            assertThat(atrasados).containsEntry(10, 50L);
            assertThat(atrasados).containsEntry(5, 10L);
            assertThat(atrasados).containsEntry(15, 0L);
        }

        @Test
        @DisplayName("deve retornar mapa vazio quando não há concursos")
        void deveRetornarVazioSemConcursos() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.MEGA_SENA))
                    .thenReturn(Optional.empty());

            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.MEGA_SENA, 10);

            assertThat(atrasados).isEmpty();
        }

        @Test
        @DisplayName("deve limitar resultado por quantidade")
        void deveLimitarPorQuantidade() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.MEGA_SENA))
                    .thenReturn(Optional.of(100));

            // Provide appearance data for all 60 numbers so none default to atraso=100
            List<Object[]> ultimaAparicao = new ArrayList<>();
            for (int i = 1; i <= 60; i++) {
                ultimaAparicao.add(new Object[]{i, 99}); // default atraso = 1
            }
            // Override specific numbers with bigger atrasos
            ultimaAparicao.set(0, new Object[]{1, 50});  // atraso = 50
            ultimaAparicao.set(1, new Object[]{2, 80});  // atraso = 20
            ultimaAparicao.set(2, new Object[]{3, 90});  // atraso = 10
            when(concursoRepository.findUltimaAparicaoDezenas("MEGA_SENA")).thenReturn(ultimaAparicao);

            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.MEGA_SENA, 2);

            assertThat(atrasados).hasSize(2);
            assertThat(atrasados).containsEntry(1, 50L);
            assertThat(atrasados).containsEntry(2, 20L);
        }

        @Test
        @DisplayName("deve ordenar por atraso decrescente (LinkedHashMap)")
        void deveOrdenarPorAtrasoDecrescente() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.MEGA_SENA))
                    .thenReturn(Optional.of(100));

            // Provide all 60 numbers so no defaults skew results
            List<Object[]> ultimaAparicao = new ArrayList<>();
            for (int i = 1; i <= 60; i++) {
                ultimaAparicao.add(new Object[]{i, 100}); // atraso = 0
            }
            ultimaAparicao.set(0, new Object[]{1, 80});  // atraso = 20
            ultimaAparicao.set(1, new Object[]{2, 50});  // atraso = 50
            ultimaAparicao.set(2, new Object[]{3, 90});  // atraso = 10
            when(concursoRepository.findUltimaAparicaoDezenas("MEGA_SENA")).thenReturn(ultimaAparicao);

            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.MEGA_SENA, 3);

            List<Long> values = new ArrayList<>(atrasados.values());
            assertThat(values).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        @DisplayName("números sem aparição devem ter atraso = ultimoConcurso (inicializados com 0)")
        void numerosNuncaSorteadosDevemTerAtrasoMaximo() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.LOTOFACIL))
                    .thenReturn(Optional.of(3200));

            // Only number 1 appeared
            List<Object[]> ultimaAparicao = new ArrayList<>();
            ultimaAparicao.add(new Object[]{1, 3200});
            when(concursoRepository.findUltimaAparicaoDezenas("LOTOFACIL")).thenReturn(ultimaAparicao);

            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.LOTOFACIL, 5);

            // Numbers 2-25 never appeared, so their atraso = 3200 - 0 = 3200
            assertThat(atrasados.values()).allMatch(v -> v == 3200L);
            assertThat(atrasados).doesNotContainKey(1);
        }

        @Test
        @DisplayName("quantidade maior que números disponíveis retorna todos")
        void quantidadeMaiorQueDisponiveisRetornaTodos() {
            when(concursoRepository.findMaxNumeroByTipoLoteria(TipoLoteria.LOTOFACIL))
                    .thenReturn(Optional.of(100));
            when(concursoRepository.findUltimaAparicaoDezenas("LOTOFACIL")).thenReturn(List.of());

            // LOTOFACIL has 25 numbers, requesting 100
            Map<Integer, Long> atrasados = estatisticaService.numerosAtrasados(TipoLoteria.LOTOFACIL, 100);

            assertThat(atrasados).hasSize(25);
        }
    }

    @Nested
    @DisplayName("numerosMaisFrequentes")
    class NumerosMaisFrequentes {

        @Test
        @DisplayName("deve retornar os N números mais frequentes ordenados por valor decrescente")
        void deveRetornarMaisFrequentes() {
            List<Object[]> mockResults = List.of(
                    new Object[]{10, 300L},
                    new Object[]{20, 250L},
                    new Object[]{30, 100L}
            );
            when(concursoRepository.findFrequenciaDezenas("MEGA_SENA")).thenReturn(mockResults);

            Map<Integer, Long> result = estatisticaService.numerosMaisFrequentes(TipoLoteria.MEGA_SENA, 2);

            assertThat(result).hasSize(2);
            List<Long> values = new ArrayList<>(result.values());
            assertThat(values).isSortedAccordingTo(Comparator.reverseOrder());
            assertThat(result).containsEntry(10, 300L);
            assertThat(result).containsEntry(20, 250L);
        }
    }

    @Nested
    @DisplayName("numerosMenosFrequentes")
    class NumerosMenosFrequentes {

        @Test
        @DisplayName("deve retornar os N números menos frequentes ordenados por valor crescente")
        void deveRetornarMenosFrequentes() {
            List<Object[]> mockResults = List.of(
                    new Object[]{10, 300L},
                    new Object[]{20, 250L},
                    new Object[]{30, 100L}
            );
            when(concursoRepository.findFrequenciaDezenas("MEGA_SENA")).thenReturn(mockResults);

            Map<Integer, Long> result = estatisticaService.numerosMenosFrequentes(TipoLoteria.MEGA_SENA, 2);

            assertThat(result).hasSize(2);
            List<Long> values = new ArrayList<>(result.values());
            assertThat(values).isSorted();
        }
    }
}
