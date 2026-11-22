package co.edu.unbosque.biohub.aplicacion.historial;

import java.time.Instant;

/** Datos de entrada y salida del módulo de historial (capas cerradas, ADR-02). */
public final class VistasHistorial {

    private VistasHistorial() {
    }

    /** Lo que envía el director científico. */
    public record NuevaReclasificacion(Long especieNuevaId, String motivo) {
    }

    public record EspecieResumen(long id, String nombreCientifico) {
    }

    public record AutorResumen(long id, String nombre) {
    }

    /** Un cambio del historial con sus 5 datos: autor, fecha, motivo, valor anterior y nuevo (HU-06, HU-07). */
    public record VistaReclasificacion(long id, long hallazgoId, EspecieResumen especieAnterior,
                                       EspecieResumen especieNueva, String motivo, AutorResumen autor,
                                       Instant realizadaEn) {
    }
}
