package co.edu.unbosque.biohub.configuracion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Activa las tareas programadas SOLO en el rol batch (T-22, T-24).
 *
 * El "latido" mantiene vivo el proceso batch mientras no hay informes que generar.
 * El paquete de analítica (Karen, HU-11) agrega aquí la tarea mensual.
 */
@Configuration
@Profile("batch")
@EnableScheduling
public class ProgramacionBatchConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramacionBatchConfig.class);

    @Scheduled(fixedRate = 3_600_000, initialDelay = 0)
    void latido() {
        LOG.info("Proceso batch activo, esperando la próxima ejecución programada");
    }
}
