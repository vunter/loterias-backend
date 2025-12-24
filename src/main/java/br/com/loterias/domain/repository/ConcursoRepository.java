package br.com.loterias.domain.repository;

import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.TipoLoteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ConcursoRepository extends JpaRepository<Concurso, Long> {

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao LEFT JOIN FETCH c.ganhadoresUF WHERE c.tipoLoteria = :tipoLoteria")
    List<Concurso> findByTipoLoteria(TipoLoteria tipoLoteria);

    @Query(value = "SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao LEFT JOIN FETCH c.ganhadoresUF WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC",
           countQuery = "SELECT COUNT(c) FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria")
    Page<Concurso> findByTipoLoteriaPaged(TipoLoteria tipoLoteria, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao WHERE c.tipoLoteria = :tipoLoteria")
    List<Concurso> findByTipoLoteriaWithDezenas(TipoLoteria tipoLoteria);

    @Query("SELECT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.dezenasSorteadasOrdemSorteio LEFT JOIN FETCH c.dezenasSegundoSorteio LEFT JOIN FETCH c.faixasPremiacao LEFT JOIN FETCH c.ganhadoresUF WHERE c.tipoLoteria = :tipoLoteria AND c.numero = :numero")
    Optional<Concurso> findByTipoLoteriaAndNumero(TipoLoteria tipoLoteria, Integer numero);

    boolean existsByTipoLoteriaAndNumero(TipoLoteria tipoLoteria, Integer numero);

    @Query("SELECT MAX(c.numero) FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria")
    Optional<Integer> findMaxNumeroByTipoLoteria(TipoLoteria tipoLoteria);

    @Query("SELECT c.numero FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria")
    Set<Integer> findNumerosByTipoLoteria(TipoLoteria tipoLoteria);

    @Query(value = "SELECT c.* FROM concurso c WHERE c.tipo_loteria = :tipoLoteria ORDER BY c.numero DESC LIMIT 1", nativeQuery = true)
    Optional<Concurso> findTopByTipoLoteriaOrderByNumeroDescSimple(@Param("tipoLoteria") String tipoLoteria);

    @Query("SELECT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.dezenasSorteadasOrdemSorteio LEFT JOIN FETCH c.dezenasSegundoSorteio LEFT JOIN FETCH c.faixasPremiacao LEFT JOIN FETCH c.ganhadoresUF WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC LIMIT 1")
    Optional<Concurso> findTopByTipoLoteriaOrderByNumeroDesc(TipoLoteria tipoLoteria);

    @Query("SELECT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.dezenasSorteadasOrdemSorteio LEFT JOIN FETCH c.dezenasSegundoSorteio WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC LIMIT 1")
    Optional<Concurso> findTopByTipoLoteriaOrderByNumeroDescWithAllCollections(TipoLoteria tipoLoteria);

    @Modifying
    @Query("DELETE FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria")
    int deleteByTipoLoteria(TipoLoteria tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas WHERE c.tipoLoteria = :tipoLoteria")
    List<Concurso> findByTipoLoteriaOnlyDezenas(TipoLoteria tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC")
    List<Concurso> findByTipoLoteriaWithDezenasAndFaixas(TipoLoteria tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao WHERE c.tipoLoteria = :tipoLoteria AND c.dataApuracao >= :dataInicio AND c.dataApuracao <= :dataFim ORDER BY c.numero DESC")
    List<Concurso> findByTipoLoteriaWithDezenasAndFaixasBetweenDates(TipoLoteria tipoLoteria, @Param("dataInicio") java.time.LocalDate dataInicio, @Param("dataFim") java.time.LocalDate dataFim);

    @Query(value = "SELECT cd.dezena, COUNT(*) as freq FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id WHERE c.tipo_loteria = :tipoLoteria GROUP BY cd.dezena ORDER BY cd.dezena", nativeQuery = true)
    List<Object[]> findFrequenciaDezenas(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = "SELECT cd.dezena, MAX(c.numero) as ultimo FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id WHERE c.tipo_loteria = :tipoLoteria GROUP BY cd.dezena", nativeQuery = true)
    List<Object[]> findUltimaAparicaoDezenas(@Param("tipoLoteria") String tipoLoteria);

    @Query("SELECT COUNT(c) FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria")
    long countByTipoLoteria(TipoLoteria tipoLoteria);

    @Query(value = "SELECT c.numero, cd.dezena FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id WHERE c.tipo_loteria = :tipoLoteria ORDER BY c.numero DESC LIMIT :limite", nativeQuery = true)
    List<Object[]> findNumerosEDezenasLimitado(@Param("tipoLoteria") String tipoLoteria, @Param("limite") int limite);

    @Query(value = "SELECT c.numero, cd.dezena FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id WHERE c.tipo_loteria = :tipoLoteria ORDER BY c.numero DESC", nativeQuery = true)
    List<Object[]> findNumerosEDezenas(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT DISTINCT c.numero FROM concurso c 
        JOIN faixa_premiacao fp ON fp.concurso_id = c.id 
        LEFT JOIN ganhador_uf gu ON gu.concurso_id = c.id
        WHERE c.tipo_loteria = :tipoLoteria 
        AND fp.faixa = 1 
        AND fp.numero_ganhadores > 0
        AND gu.id IS NULL
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosComGanhadoresSemDetalhes(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.* FROM concurso c 
        JOIN faixa_premiacao fp ON fp.concurso_id = c.id 
        WHERE c.tipo_loteria = :tipoLoteria 
        AND fp.faixa = 1 
        AND fp.numero_ganhadores > 0
        ORDER BY c.numero DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Concurso> findUltimoConcursoComGanhador(@Param("tipoLoteria") String tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadasOrdemSorteio WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC")
    List<Concurso> findByTipoLoteriaWithOrdemSorteio(TipoLoteria tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.dezenasSegundoSorteio LEFT JOIN FETCH c.faixasPremiacao WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero DESC")
    List<Concurso> findByTipoLoteriaWithSegundoSorteio(TipoLoteria tipoLoteria);

    @Query("SELECT c.numero FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria AND c.nomeMunicipioUFSorteio IS NULL")
    List<Integer> findNumerosComLocalSorteioFaltando(TipoLoteria tipoLoteria);

    @Query("SELECT c.numero FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria AND c.nomeTimeCoracaoMesSorte IS NULL")
    List<Integer> findNumerosComTimeCoracaoFaltando(TipoLoteria tipoLoteria);

    @Query(value = """
        SELECT DISTINCT c.numero FROM concurso c 
        JOIN faixa_premiacao fp ON fp.concurso_id = c.id 
        WHERE c.tipo_loteria = :tipoLoteria 
        AND fp.faixa = 1 
        AND fp.numero_ganhadores > 0
        AND c.nome_municipio_uf_sorteio IS NULL
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosComGanhadoresSemLocalSorteio(@Param("tipoLoteria") String tipoLoteria);

    @Query("SELECT c.numero FROM Concurso c WHERE c.tipoLoteria = :tipoLoteria ORDER BY c.numero")
    List<Integer> findAllNumerosByTipoLoteria(TipoLoteria tipoLoteria);

    @Modifying
    @Query("UPDATE Concurso c SET c.nomeMunicipioUFSorteio = NULL WHERE c.tipoLoteria = :tipoLoteria")
    int limparLocalSorteio(TipoLoteria tipoLoteria);

    @Modifying
    @Query("UPDATE Concurso c SET c.nomeTimeCoracaoMesSorte = NULL WHERE c.tipoLoteria = :tipoLoteria")
    int limparTimeCoracaoMesSorte(TipoLoteria tipoLoteria);

    @Query(value = """
        SELECT DISTINCT c.numero FROM concurso c 
        JOIN faixa_premiacao fp ON fp.concurso_id = c.id 
        WHERE c.tipo_loteria = :tipoLoteria 
        AND fp.faixa = 1 
        AND fp.numero_ganhadores > 0
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosComGanhadoresFaixaPrincipal(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = "SELECT c.nome_time_coracao_mes_sorte as nome, COUNT(*) as freq FROM concurso c WHERE c.tipo_loteria = :tipoLoteria AND c.nome_time_coracao_mes_sorte IS NOT NULL GROUP BY c.nome_time_coracao_mes_sorte ORDER BY freq DESC", nativeQuery = true)
    List<Object[]> findFrequenciaTimeCoracao(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = "SELECT c.nome_time_coracao_mes_sorte, MAX(c.numero) as ultimo FROM concurso c WHERE c.tipo_loteria = :tipoLoteria AND c.nome_time_coracao_mes_sorte IS NOT NULL GROUP BY c.nome_time_coracao_mes_sorte", nativeQuery = true)
    List<Object[]> findUltimaAparicaoTimeCoracao(@Param("tipoLoteria") String tipoLoteria);

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.faixasPremiacao WHERE c.tipoLoteria = :tipoLoteria AND c.numero BETWEEN :inicio AND :fim ORDER BY c.numero")
    List<Concurso> findByTipoLoteriaAndNumeroRange(TipoLoteria tipoLoteria, int inicio, int fim);

    @Query(value = """
        SELECT cd.dezena, COUNT(*) as freq 
        FROM concurso c 
        JOIN concurso_dezenas cd ON c.id = cd.concurso_id 
        JOIN faixa_premiacao fp ON fp.concurso_id = c.id 
        WHERE c.tipo_loteria = :tipoLoteria 
        AND fp.faixa = 1 
        AND fp.numero_ganhadores > 0
        GROUP BY cd.dezena ORDER BY cd.dezena
        """, nativeQuery = true)
    List<Object[]> findFrequenciaDezenasComGanhadores(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.numero FROM concurso c 
        JOIN concurso_dezenas cd ON c.id = cd.concurso_id 
        WHERE c.tipo_loteria = :tipoLoteria AND cd.dezena = :dezena 
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosComDezena(@Param("tipoLoteria") String tipoLoteria, @Param("dezena") int dezena);

    @Query(value = """
        SELECT 
            ROUND(AVG(pares)::numeric, 2) as media_pares, 
            ROUND(AVG(impares)::numeric, 2) as media_impares
        FROM (
            SELECT c.id,
                SUM(CASE WHEN cd.dezena % 2 = 0 THEN 1 ELSE 0 END) as pares,
                SUM(CASE WHEN cd.dezena % 2 != 0 THEN 1 ELSE 0 END) as impares
            FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id
            WHERE c.tipo_loteria = :tipoLoteria
            GROUP BY c.id
        ) sub
        """, nativeQuery = true)
    Object[] findMediaParesImpares(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT ROUND(AVG(soma)::numeric, 2) FROM (
            SELECT c.id, SUM(cd.dezena) as soma
            FROM concurso c JOIN concurso_dezenas cd ON c.id = cd.concurso_id
            WHERE c.tipo_loteria = :tipoLoteria
            GROUP BY c.id
        ) sub
        """, nativeQuery = true)
    Double findSomaMedia(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.numero, c.acumulado
        FROM concurso c
        WHERE c.tipo_loteria = :tipoLoteria
        ORDER BY c.numero DESC
        LIMIT :limite
        """, nativeQuery = true)
    List<Object[]> findUltimosAcumulados(@Param("tipoLoteria") String tipoLoteria, @Param("limite") int limite);

    @Query(value = """
        SELECT gu.uf, gu.cidade, SUM(gu.numero_ganhadores) as total_ganhadores, COUNT(DISTINCT c.id) as total_concursos
        FROM ganhador_uf gu
        JOIN concurso c ON gu.concurso_id = c.id
        WHERE c.tipo_loteria = :tipoLoteria AND gu.faixa = 1 AND gu.uf IS NOT NULL
        GROUP BY gu.uf, gu.cidade
        ORDER BY total_ganhadores DESC
        """, nativeQuery = true)
    List<Object[]> findGanhadoresPorUFCidade(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT gu.uf, SUM(gu.numero_ganhadores) as total_ganhadores, COUNT(DISTINCT c.id) as total_concursos
        FROM ganhador_uf gu
        JOIN concurso c ON gu.concurso_id = c.id
        WHERE c.tipo_loteria = :tipoLoteria AND gu.faixa = 1 AND gu.uf IS NOT NULL
        GROUP BY gu.uf
        ORDER BY total_ganhadores DESC
        """, nativeQuery = true)
    List<Object[]> findGanhadoresPorUF(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT MIN(c.numero) FROM concurso c
        WHERE c.tipo_loteria = :tipoLoteria
        AND c.data_apuracao >= CURRENT_DATE - CAST(:dias AS INTEGER)
        """, nativeQuery = true)
    Optional<Integer> findMinNumeroDesde(@Param("tipoLoteria") String tipoLoteria, @Param("dias") int dias);

    @Query(value = """
        SELECT MIN(c.data_apuracao) FROM concurso c
        JOIN ganhador_uf gu ON gu.concurso_id = c.id
        WHERE c.tipo_loteria = :tipoLoteria AND gu.faixa = 1
        AND gu.cidade IS NOT NULL AND TRIM(gu.cidade) <> ''
        """, nativeQuery = true)
    Optional<java.time.LocalDate> findEarliestConcursoWithCityName(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.numero FROM concurso c
        WHERE c.tipo_loteria = :tipoLoteria
        AND NOT EXISTS (SELECT 1 FROM faixa_premiacao fp WHERE fp.concurso_id = c.id)
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosSemFaixas(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.numero FROM concurso c
        WHERE c.tipo_loteria = :tipoLoteria
        AND (
            NOT EXISTS (SELECT 1 FROM faixa_premiacao fp WHERE fp.concurso_id = c.id)
            OR c.valor_arrecadado IS NULL
            OR c.valor_arrecadado = 0
        )
        ORDER BY c.numero
        """, nativeQuery = true)
    List<Integer> findConcursosIncompletos(@Param("tipoLoteria") String tipoLoteria);

    @Query(value = """
        SELECT c.tipo_loteria,
          COUNT(*) as total,
          COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM faixa_premiacao fp WHERE fp.concurso_id = c.id)) as sem_faixas
        FROM concurso c
        GROUP BY c.tipo_loteria
        ORDER BY c.tipo_loteria
        """, nativeQuery = true)
    List<Object[]> countConcursosSemFaixasPorTipo();

    @Query(value = """
        SELECT c.tipo_loteria,
          COUNT(*) as total,
          COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM faixa_premiacao fp WHERE fp.concurso_id = c.id)) as sem_faixas,
          COUNT(*) FILTER (WHERE c.valor_arrecadado IS NULL OR c.valor_arrecadado = 0) as sem_arrecadacao,
          COUNT(*) FILTER (
            WHERE NOT EXISTS (SELECT 1 FROM faixa_premiacao fp WHERE fp.concurso_id = c.id)
            OR c.valor_arrecadado IS NULL OR c.valor_arrecadado = 0
          ) as incompletos
        FROM concurso c
        GROUP BY c.tipo_loteria
        ORDER BY c.tipo_loteria
        """, nativeQuery = true)
    List<Object[]> countConcursosIncompletosPorTipo();

    // --- Dupla Sena optimized queries ---

    @Query(value = """
        SELECT cd.dezena, COUNT(*) as freq
        FROM concurso c
        JOIN concurso_dezenas cd ON c.id = cd.concurso_id
        WHERE c.tipo_loteria = 'DUPLA_SENA'
        GROUP BY cd.dezena ORDER BY cd.dezena
        """, nativeQuery = true)
    List<Object[]> findFreqPrimeiroSorteioDupla();

    @Query(value = """
        SELECT ds.dezena, COUNT(*) as freq
        FROM concurso c
        JOIN concurso_dezenas_segundo_sorteio ds ON c.id = ds.concurso_id
        WHERE c.tipo_loteria = 'DUPLA_SENA'
        GROUP BY ds.dezena ORDER BY ds.dezena
        """, nativeQuery = true)
    List<Object[]> findFreqSegundoSorteioDupla();

    @Query(value = """
        SELECT coincidencias, COUNT(*) as freq FROM (
            SELECT c.id,
                (SELECT COUNT(*) FROM concurso_dezenas cd
                 JOIN concurso_dezenas_segundo_sorteio ds ON cd.concurso_id = ds.concurso_id AND cd.dezena = ds.dezena
                 WHERE cd.concurso_id = c.id) as coincidencias
            FROM concurso c
            WHERE c.tipo_loteria = 'DUPLA_SENA'
            AND EXISTS (SELECT 1 FROM concurso_dezenas_segundo_sorteio ds2 WHERE ds2.concurso_id = c.id)
        ) sub
        GROUP BY coincidencias ORDER BY coincidencias
        """, nativeQuery = true)
    List<Object[]> findDistribuicaoCoincidenciasDupla();

    @Query(value = """
        SELECT COUNT(*) FROM concurso c
        WHERE c.tipo_loteria = 'DUPLA_SENA'
        AND EXISTS (SELECT 1 FROM concurso_dezenas_segundo_sorteio ds WHERE ds.concurso_id = c.id)
        """, nativeQuery = true)
    int countDuplaSenaCompletos();

    @Query(value = """
        SELECT AVG(coincidencias), MAX(coincidencias), MIN(coincidencias) FROM (
            SELECT c.id,
                (SELECT COUNT(*) FROM concurso_dezenas cd
                 JOIN concurso_dezenas_segundo_sorteio ds ON cd.concurso_id = ds.concurso_id AND cd.dezena = ds.dezena
                 WHERE cd.concurso_id = c.id) as coincidencias
            FROM concurso c
            WHERE c.tipo_loteria = 'DUPLA_SENA'
            AND EXISTS (SELECT 1 FROM concurso_dezenas_segundo_sorteio ds2 WHERE ds2.concurso_id = c.id)
        ) sub
        """, nativeQuery = true)
    Object[] findCoincidenciasStatsDupla();

    @Query(value = "SELECT c.id FROM concurso c WHERE c.tipo_loteria = 'DUPLA_SENA' AND EXISTS (SELECT 1 FROM concurso_dezenas_segundo_sorteio ds WHERE ds.concurso_id = c.id) ORDER BY c.numero DESC LIMIT 20", nativeQuery = true)
    List<Long> findTop20DuplaSenaIds();

    // --- Especiais: batch load latest concurso per lottery ---

    @Query(value = """
        SELECT c.* FROM concurso c
        INNER JOIN (
            SELECT tipo_loteria, MAX(numero) as max_numero
            FROM concurso GROUP BY tipo_loteria
        ) latest ON c.tipo_loteria = latest.tipo_loteria AND c.numero = latest.max_numero
        """, nativeQuery = true)
    List<Concurso> findLatestConcursoPerTipoLoteria();

    @Query("SELECT DISTINCT c FROM Concurso c LEFT JOIN FETCH c.dezenasSorteadas LEFT JOIN FETCH c.dezenasSorteadasOrdemSorteio LEFT JOIN FETCH c.dezenasSegundoSorteio WHERE c.tipoLoteria = :tipo AND c.numero = :numero")
    Optional<Concurso> findWithCollectionsByTipoAndNumero(@Param("tipo") TipoLoteria tipo, @Param("numero") int numero);
}
