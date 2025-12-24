package br.com.loterias.domain.repository;

import br.com.loterias.domain.entity.TimeTimemania;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimeTimemaniaRepository extends JpaRepository<TimeTimemania, Long> {

    Optional<TimeTimemania> findByCodigoCaixa(Integer codigoCaixa);

    Optional<TimeTimemania> findByNomeCompleto(String nomeCompleto);

    List<TimeTimemania> findByUf(String uf);

    List<TimeTimemania> findByAtivoTrue();

    List<TimeTimemania> findByNomeContainingIgnoreCase(String nome);

    boolean existsByNomeCompleto(String nomeCompleto);

    boolean existsByCodigoCaixa(Integer codigoCaixa);
}
