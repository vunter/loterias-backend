package br.com.loterias.service;

import br.com.loterias.domain.dto.CaixaApiResponse;
import br.com.loterias.domain.dto.GanhadorDTO;
import br.com.loterias.domain.dto.RateioPremioDTO;
import br.com.loterias.domain.entity.Concurso;
import br.com.loterias.domain.entity.FaixaPremiacao;
import br.com.loterias.domain.entity.GanhadorUF;
import br.com.loterias.domain.entity.TipoLoteria;
import br.com.loterias.domain.repository.ConcursoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AtualizarGanhadoresService {

    private static final Logger log = LoggerFactory.getLogger(AtualizarGanhadoresService.class);

    private final ConcursoRepository concursoRepository;
    private final CaixaApiClient caixaApiClient;

    public AtualizarGanhadoresService(ConcursoRepository concursoRepository, CaixaApiClient caixaApiClient) {
        this.concursoRepository = concursoRepository;
        this.caixaApiClient = caixaApiClient;
    }

    private static final int MAX_CONCURRENT_API_CALLS = 5;

    public int atualizarConcursosComGanhadores(TipoLoteria tipo) {
        log.info("Buscando concursos de {} com ganhadores na faixa principal para atualizar...", tipo.getNome());

        List<Integer> concursosComGanhadores = concursoRepository.findConcursosComGanhadoresSemDetalhes(tipo.name());
        log.info("Encontrados {} concursos de {} para atualizar", concursosComGanhadores.size(), tipo.getNome());

        if (concursosComGanhadores.isEmpty()) {
            return 0;
        }

        AtomicInteger atualizados = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_API_CALLS);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Integer numero : concursosComGanhadores) {
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            boolean atualizado = atualizarConcurso(tipo, numero);
                            if (atualizado) {
                                int count = atualizados.incrementAndGet();
                                if (count % 50 == 0) {
                                    log.info("Progresso {}: {} concursos atualizados", tipo.getNome(), count);
                                }
                            }
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted while updating concurso {} de {}", numero, tipo.getNome());
                    } catch (Exception e) {
                        log.error("Erro ao atualizar concurso {} de {}: {}", numero, tipo.getNome(), e.getMessage());
                        erros.incrementAndGet();
                    }
                });
            }
        }

        log.info("Atualização de {} finalizada. Atualizados: {}, Erros: {}", tipo.getNome(), atualizados.get(), erros.get());
        return atualizados.get();
    }

    @Transactional
    public boolean atualizarConcurso(TipoLoteria tipo, Integer numero) {
        Optional<Concurso> concursoOpt = concursoRepository.findByTipoLoteriaAndNumero(tipo, numero);
        if (concursoOpt.isEmpty()) {
            log.warn("Concurso {} de {} não encontrado no banco", numero, tipo.getNome());
            return false;
        }

        Optional<CaixaApiResponse> responseOpt = caixaApiClient.buscarConcurso(tipo, numero);
        if (responseOpt.isEmpty()) {
            log.warn("Concurso {} de {} não encontrado na API", numero, tipo.getNome());
            return false;
        }

        Concurso concurso = concursoOpt.get();
        CaixaApiResponse response = responseOpt.get();

        concurso.getFaixasPremiacao().clear();
        if (response.listaRateioPremio() != null) {
            for (RateioPremioDTO rateio : response.listaRateioPremio()) {
                FaixaPremiacao faixa = new FaixaPremiacao(
                        rateio.faixa(),
                        rateio.descricaoFaixa(),
                        rateio.numeroDeGanhadores(),
                        rateio.valorPremio()
                );
                concurso.addFaixaPremiacao(faixa);
            }
        }

        concurso.getGanhadoresUF().clear();
        if (response.listaMunicipioUFGanhadores() != null) {
            for (GanhadorDTO ganhador : response.listaMunicipioUFGanhadores()) {
                GanhadorUF ganhadorUF = new GanhadorUF(
                        ganhador.uf(),
                        ganhador.municipio(),
                        ganhador.ganhadores(),
                        1,
                        ganhador.canal()
                );
                concurso.addGanhadorUF(ganhadorUF);
            }
        }

        if (response.localSorteio() != null) {
            concurso.setLocalSorteio(response.localSorteio());
        }
        if (response.valorArrecadado() != null) {
            concurso.setValorArrecadado(response.valorArrecadado());
        }

        concursoRepository.save(concurso);
        log.debug("Concurso {} de {} atualizado com {} faixas e {} ganhadores",
                numero, tipo.getNome(),
                concurso.getFaixasPremiacao().size(),
                concurso.getGanhadoresUF().size());

        return true;
    }

    public Map<String, Integer> atualizarTodosComGanhadores() {
        log.info("Iniciando atualização de ganhadores de todas as loterias com virtual threads...");
        
        Map<String, Integer> resultados = new ConcurrentHashMap<>();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TipoLoteria tipo : TipoLoteria.values()) {
                executor.submit(() -> {
                    int atualizados = atualizarConcursosComGanhadores(tipo);
                    resultados.put(tipo.getNome(), atualizados);
                });
            }
        }
        
        int total = resultados.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Atualização de todas as loterias finalizada. Total: {}", total);
        
        return resultados;
    }
}
