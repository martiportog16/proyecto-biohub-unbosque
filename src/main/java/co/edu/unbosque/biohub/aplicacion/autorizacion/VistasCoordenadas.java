package co.edu.unbosque.biohub.aplicacion.autorizacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Lo que entrega el componente de autorización (capas cerradas, ADR-02). */
public final class VistasCoordenadas {

    private VistasCoordenadas() {
    }

    /** La ubicación exacta de un hallazgo. Solo se arma después de que la regla lo permite. */
    public record VistaCoordenada(long hallazgoId, String departamento, LocalDate fechaObservacion,
                                  double latitud, double longitud, Integer precisionMetros) {
    }

    /** Los puntos de una especie para dibujar el mapa (HU-08, criterio 1). */
    public record VistaMapa(long especieId, String nombreCientifico, boolean enPeligro, int total,
                            List<VistaCoordenada> puntos) {
    }

    /** Una fila del registro de accesos, para el auditor. No contiene coordenadas. */
    public record VistaAcceso(long id, long usuarioId, String rol, long especieId, Long hallazgoId,
                              boolean especieEnPeligro, boolean permitido, String motivo, Instant ocurridoEn) {
    }
}
