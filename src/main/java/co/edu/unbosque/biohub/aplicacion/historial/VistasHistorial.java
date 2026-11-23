package co.edu.unbosque.biohub.aplicacion.historial;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    /** El historial completo de un hallazgo, en orden cronológico (HU-07, criterio 1). */
    public record VistaHistorial(long hallazgoId, Instant registradoEn, EspecieResumen especieInicial,
                                 EspecieResumen especieActual, List<VistaReclasificacion> cambios) {
    }

    /**
     * El estado en una fecha frente al estado actual (HU-07, criterio 3).
     * Si en esa fecha el hallazgo aún no existía, "existiaEnEsaFecha" es false, "especieEnEsaFecha" es null
     * y "cambiosDesdeEntonces" va vacía.
     * "cambioDesdeEntonces" dice si la especie de HOY es distinta a la de esa fecha. Puede ser false aunque
     * haya cambios en el medio (de A a B y de vuelta a A): por eso también va la lista.
     */
    public record VistaComparacion(long hallazgoId, LocalDate fecha, boolean existiaEnEsaFecha,
                                   EspecieResumen especieEnEsaFecha, EspecieResumen especieActual,
                                   boolean cambioDesdeEntonces, List<VistaReclasificacion> cambiosDesdeEntonces) {
    }
}
