package co.edu.unbosque.biohub.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import co.edu.unbosque.biohub.aplicacion.analitica.ProcesoInformes;

/**
 * Las tareas programadas del proceso batch (T-22, T-24, P-11).
 * Solo existen con el perfil "batch": los grupos público y científico nunca
 * generan informes (HU-11, criterio 2).
 *
 * Spring ejecuta estas tareas en un solo hilo, una tras otra: nunca se generan
 * dos informes a la vez.
 */
@Configuration
@Profile("batch")
@EnableScheduling
public class ProgramacionBatchConfig {

    private final ProcesoInformes proceso;

    public ProgramacionBatchConfig(ProcesoInformes proceso) {
        this.proceso = proceso;
    }

    /** El día 1 de cada mes a las 2:00 a. m., hora de Colombia: informe del mes que terminó. */
    @Scheduled(cron = "${biohub.batch.cron-mensual:0 0 2 1 * *}", zone = "America/Bogota")
    void informeMensual() {
        proceso.generarMesAnterior();
    }

    /** Cada 30 s: ¿el analista pidió algún informe? (se atiende en menos de 1 minuto). */
    @Scheduled(fixedDelayString = "${biohub.batch.revisar-solicitudes-ms:30000}",
               initialDelayString = "${biohub.batch.espera-inicial-ms:20000}")
    void solicitudesDelAnalista() {
        proceso.atenderSolicitudes();
    }

    /** Al arrancar y cada hora: si el batch estuvo apagado el día 1, genera el informe que faltó. */
    @Scheduled(fixedRateString = "${biohub.batch.ponerse-al-dia-ms:3600000}",
               initialDelayString = "${biohub.batch.espera-inicial-ms:20000}")
    void ponerseAlDia() {
        proceso.ponerseAlDia();
    }
}
