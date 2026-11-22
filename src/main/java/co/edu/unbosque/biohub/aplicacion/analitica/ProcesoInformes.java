package co.edu.unbosque.biohub.aplicacion.analitica;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;
import co.edu.unbosque.biohub.persistencia.analitica.RepositorioInformes;

/**
 * Lo que hace el proceso batch (P-11). Lo llaman las tareas programadas de
 * ProgramacionBatchConfig, que solo existen en el rol batch.
 *
 * Ningún método deja escapar excepciones: si algo falla (por ejemplo, la base
 * no responde), se anota en el log y se reintenta en la siguiente vuelta.
 */
@Service
public class ProcesoInformes {

    /** El informe mensual está programado para el día 1 a las 2:00 a. m. (T-24). */
    static final int HORA_PROGRAMADA = 2;

    private static final Logger LOG = LoggerFactory.getLogger(ProcesoInformes.class);

    private final GeneradorInforme generador;
    private final RepositorioInformes informes;
    private final Clock reloj;

    public ProcesoInformes(GeneradorInforme generador, RepositorioInformes informes, Clock reloj) {
        this.generador = generador;
        this.informes = informes;
        this.reloj = reloj;
    }

    /** Tarea mensual: el día 1 genera el informe del mes que acaba de terminar. */
    public void generarMesAnterior() {
        YearMonth mesAnterior = YearMonth.now(reloj).minusMonths(1);
        try {
            generador.generar(mesAnterior);
        } catch (RuntimeException e) {
            LOG.error("No se pudo generar el informe de {}. Se reintentará en la próxima revisión", mesAnterior, e);
        }
    }

    /**
     * Si el batch estaba apagado el día 1 a las 2:00 a. m., el informe del mes pasado
     * no se generó. Esta revisión (al arrancar y cada hora) lo detecta y lo genera.
     * Antes de la hora programada no hace nada.
     */
    public void ponerseAlDia() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDateTime programado = YearMonth.from(ahora).atDay(1).atTime(HORA_PROGRAMADA, 0);
        if (ahora.isBefore(programado)) {
            return;
        }
        try {
            if (!informes.existe(YearMonth.from(ahora).minusMonths(1))) {
                generarMesAnterior();
            }
        } catch (RuntimeException e) {
            LOG.warn("No se pudo revisar si falta el informe del mes pasado: {}", e.getMessage());
        }
    }

    /** Atiende las solicitudes pendientes del analista, de la más antigua a la más nueva. */
    public int atenderSolicitudes() {
        int atendidas = 0;
        try {
            for (SolicitudInforme solicitud : informes.solicitudesPendientes()) {
                if (atender(solicitud)) {
                    atendidas++;
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron revisar las solicitudes de informes: {}", e.getMessage());
        }
        return atendidas;
    }

    private boolean atender(SolicitudInforme solicitud) {
        try {
            generador.generar(solicitud.periodo());
        } catch (ReglaDeNegocioException e) {
            // Una solicitud que no se puede cumplir no debe quedarse pendiente para siempre.
            LOG.warn("Solicitud {} descartada: {}", solicitud.id(), e.getDetalles());
        } catch (RuntimeException e) {
            // Un fallo técnico: queda pendiente y se reintenta. Las demás solicitudes siguen.
            LOG.error("Falló la solicitud {} ({}). Queda pendiente", solicitud.id(), solicitud.periodo(), e);
            return false;
        }
        informes.marcarAtendida(solicitud.id(), Instant.now(reloj));
        return true;
    }
}
