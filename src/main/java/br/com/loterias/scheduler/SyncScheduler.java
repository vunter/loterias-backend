package br.com.loterias.scheduler;

import br.com.loterias.service.ConcursoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "loterias.sync.enabled", havingValue = "true", matchIfMissing = false)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final ConcursoSyncService concursoSyncService;

    public SyncScheduler(ConcursoSyncService concursoSyncService) {
        this.concursoSyncService = concursoSyncService;
    }

    @Scheduled(cron = "${loterias.sync.cron:0 0 22 * * *}")
    public void sincronizarConcursos() {
        log.info("Iniciando sincronização automática de todos os tipos de loteria");
        
        try {
            Map<String, ConcursoSyncService.SyncResult> resultados = concursoSyncService.sincronizarUltimosConcursosTodos();
            int totalSincronizados = resultados.values().stream()
                    .mapToInt(ConcursoSyncService.SyncResult::sincronizados).sum();
            
            resultados.forEach((nome, result) -> {
                if (result.sincronizados() > 0) {
                    log.info("{}: {}", nome, result.mensagem());
                }
            });
            
            log.info("Sincronização automática finalizada. Total sincronizado: {}", totalSincronizados);
        } catch (Exception e) {
            log.error("Erro na sincronização automática: {}", e.getMessage(), e);
        }
    }
}
