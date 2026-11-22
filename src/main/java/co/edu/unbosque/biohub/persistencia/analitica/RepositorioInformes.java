package co.edu.unbosque.biohub.persistencia.analitica;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;

/**
 * Repositorio de informes (C-1, aprobado): guarda y consulta los informes y las
 * solicitudes del analista. Siempre trabaja contra la base PRIMARIA.
 *
 * No hay métodos para editar ni borrar un informe.
 */
public interface RepositorioInformes {

    boolean existe(YearMonth periodo);

    /**
     * Guarda el informe y todos sus índices en UNA transacción.
     * Lanza DuplicateKeyException si ya había un informe de ese mes.
     */
    void guardar(InformeMensual informe);

    Optional<InformeMensual> buscar(YearMonth periodo);

    /** Resumen (sin índices) de los informes más recientes. */
    List<ResumenInforme> recientes(int limite);

    long crearSolicitud(YearMonth periodo, long usuarioId, Instant cuando);

    Optional<SolicitudInforme> solicitudPendiente(YearMonth periodo);

    /** Las pendientes, de la más antigua a la más nueva. */
    List<SolicitudInforme> solicitudesPendientes();

    List<SolicitudInforme> solicitudesRecientes(int limite);

    void marcarAtendida(long solicitudId, Instant cuando);

    record ResumenInforme(YearMonth periodo, Instant generadoEn, int totalDepartamentos) {
    }
}
