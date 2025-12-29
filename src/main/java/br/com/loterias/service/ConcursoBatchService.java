package br.com.loterias.service;

import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.GanhadorUF;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import br.com.loterias.service.util.TextCleaningUtils;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ConcursoBatchService {

    private static final Logger log = LoggerFactory.getLogger(ConcursoBatchService.class);

    private final ConcursoRepository concursoRepository;
    private final EntityManager entityManager;

    public ConcursoBatchService(ConcursoRepository concursoRepository, EntityManager entityManager) {
        this.concursoRepository = concursoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int salvarBatch(List<Concurso> concursos) {
        try {
            concursoRepository.saveAll(concursos);
            entityManager.flush();
            return concursos.size();
        } catch (Exception e) {
            log.warn("Erro ao salvar batch de {} concursos: {}", concursos.size(), e.getMessage());
            entityManager.clear();
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Set<Integer> findNumerosByTipoLoteria(TipoLoteria tipo) {
        return concursoRepository.findNumerosByTipoLoteria(tipo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Concurso salvarConcurso(Concurso concurso) {
        Concurso saved = concursoRepository.save(concurso);
        entityManager.flush();
        return saved;
    }

    @Transactional
    public int deleteByTipoLoteria(TipoLoteria tipo) {
        log.info("Deletando concursos de {}", tipo.getNome());
        
        int deletedFaixas = entityManager.createNativeQuery(
            "DELETE FROM faixa_premiacao WHERE concurso_id IN (SELECT id FROM concurso WHERE tipo_loteria = :tipo)")
            .setParameter("tipo", tipo.name())
            .executeUpdate();
        log.info("{} faixas de premiação deletadas", deletedFaixas);
        
        int deletedGanhadores = entityManager.createNativeQuery(
            "DELETE FROM ganhador_uf WHERE concurso_id IN (SELECT id FROM concurso WHERE tipo_loteria = :tipo)")
            .setParameter("tipo", tipo.name())
            .executeUpdate();
        log.info("{} ganhadores UF deletados", deletedGanhadores);
        
        int deletedDezenas = entityManager.createNativeQuery(
            "DELETE FROM concurso_dezenas WHERE concurso_id IN (SELECT id FROM concurso WHERE tipo_loteria = :tipo)")
            .setParameter("tipo", tipo.name())
            .executeUpdate();
        log.info("{} dezenas deletadas", deletedDezenas);
        
        int deleted = concursoRepository.deleteByTipoLoteria(tipo);
        entityManager.flush();
        entityManager.clear();
        log.info("{} concursos de {} deletados", deleted, tipo.getNome());
        return deleted;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean atualizarConcursoComDadosApi(TipoLoteria tipo, int numero, CaixaApiResponse apiData) {
        Optional<Concurso> existenteOpt = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
        if (existenteOpt.isEmpty()) {
            return false;
        }
        
        Concurso concurso = existenteOpt.get();
        boolean alterado = false;
        
        if (concurso.getLocalSorteio() == null && apiData.localSorteio() != null) {
            concurso.setLocalSorteio(apiData.localSorteio());
            alterado = true;
        }
        if (concurso.getNomeMunicipioUFSorteio() == null && apiData.nomeMunicipioUFSorteio() != null) {
            concurso.setNomeMunicipioUFSorteio(apiData.nomeMunicipioUFSorteio());
            alterado = true;
        }
        if (concurso.getNomeTimeCoracaoMesSorte() == null && apiData.nomeTimeCoracaoMesSorte() != null) {
            String cleaned = TextCleaningUtils.cleanNomeTimeCoracao(apiData.nomeTimeCoracaoMesSorte());
            if (cleaned != null) {
                concurso.setNomeTimeCoracaoMesSorte(cleaned);
                alterado = true;
            }
        }
        if (concurso.getValorArrecadado() == null && apiData.valorArrecadado() != null) {
            concurso.setValorArrecadado(apiData.valorArrecadado());
            alterado = true;
        }
        if (concurso.getValorAcumuladoProximoConcurso() == null && apiData.valorAcumuladoProximoConcurso() != null) {
            concurso.setValorAcumuladoProximoConcurso(apiData.valorAcumuladoProximoConcurso());
            alterado = true;
        }
        if (concurso.getValorEstimadoProximoConcurso() == null && apiData.valorEstimadoProximoConcurso() != null) {
            concurso.setValorEstimadoProximoConcurso(apiData.valorEstimadoProximoConcurso());
            alterado = true;
        }
        if (concurso.getValorAcumuladoConcursoEspecial() == null && apiData.valorAcumuladoConcursoEspecial() != null) {
            concurso.setValorAcumuladoConcursoEspecial(apiData.valorAcumuladoConcursoEspecial());
            alterado = true;
        }
        if (concurso.getValorAcumuladoConcurso05() == null && apiData.valorAcumuladoConcurso05() != null) {
            concurso.setValorAcumuladoConcurso05(apiData.valorAcumuladoConcurso05());
            alterado = true;
        }
        if (concurso.getValorSaldoReservaGarantidora() == null && apiData.valorSaldoReservaGarantidora() != null) {
            concurso.setValorSaldoReservaGarantidora(apiData.valorSaldoReservaGarantidora());
            alterado = true;
        }
        if (concurso.getValorTotalPremioFaixaUm() == null && apiData.valorTotalPremioFaixaUm() != null) {
            concurso.setValorTotalPremioFaixaUm(apiData.valorTotalPremioFaixaUm());
            alterado = true;
        }
        if (concurso.getIndicadorConcursoEspecial() == null && apiData.indicadorConcursoEspecial() != null) {
            concurso.setIndicadorConcursoEspecial(apiData.indicadorConcursoEspecial());
            alterado = true;
        }
        if (concurso.getObservacao() == null && apiData.observacao() != null) {
            concurso.setObservacao(apiData.observacao());
            alterado = true;
        }
        if ((concurso.getDezenasSorteadasOrdemSorteio() == null || concurso.getDezenasSorteadasOrdemSorteio().isEmpty()) 
                && apiData.dezenasSorteadasOrdemSorteio() != null && !apiData.dezenasSorteadasOrdemSorteio().isEmpty()) {
            concurso.setDezenasSorteadasOrdemSorteio(
                    new ArrayList<>(apiData.dezenasSorteadasOrdemSorteio().stream().map(Integer::parseInt).toList()));
            alterado = true;
        }
        
        if (concurso.getGanhadoresUF().isEmpty() && apiData.listaMunicipioUFGanhadores() != null 
                && !apiData.listaMunicipioUFGanhadores().isEmpty()) {
            for (var ganhador : apiData.listaMunicipioUFGanhadores()) {
                concurso.addGanhadorUF(new GanhadorUF(
                        ganhador.uf(), ganhador.municipio(), ganhador.ganhadores(), 1, ganhador.canal()));
            }
            alterado = true;
        }
        
        if (alterado) {
            concursoRepository.save(concurso);
            entityManager.flush();
        }
        
        return alterado;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean forcarAtualizacaoLocalSorteio(TipoLoteria tipo, int numero, CaixaApiResponse apiData) {
        Optional<Concurso> existenteOpt = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
        if (existenteOpt.isEmpty()) {
            return false;
        }
        
        Concurso concurso = existenteOpt.get();
        boolean alterado = false;
        
        // Forçar atualização do local de sorteio (sobrescreve valor existente)
        if (apiData.nomeMunicipioUFSorteio() != null) {
            concurso.setNomeMunicipioUFSorteio(apiData.nomeMunicipioUFSorteio());
            alterado = true;
        }
        
        // Também atualiza time do coração se disponível
        String timeCoracao = TextCleaningUtils.cleanNomeTimeCoracao(apiData.nomeTimeCoracaoMesSorte());
        if (timeCoracao != null) {
            concurso.setNomeTimeCoracaoMesSorte(timeCoracao);
            alterado = true;
        }
        
        if (alterado) {
            concursoRepository.save(concurso);
            entityManager.flush();
        }
        
        return alterado;
    }
}
