package co.edu.unbosque.biohub.dominio.analitica;

import java.time.Instant;
import java.time.YearMonth;

/**
 * El analista pidió generar el informe de un mes (P-11). El batch la atiende.
 *
 * @param atendidaEn null mientras está pendiente
 * @param conInforme si ya existe el informe de ese mes. Una solicitud atendida SIN
 *                   informe significa que no había hallazgos para calcularlo.
 */
public record SolicitudInforme(Long id, YearMonth periodo, long solicitadoPor, Instant solicitadoEn,
                               Instant atendidaEn, boolean conInforme) {

    public boolean pendiente() {
        return atendidaEn == null;
    }
}
