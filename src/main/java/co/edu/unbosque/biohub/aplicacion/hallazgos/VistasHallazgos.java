package co.edu.unbosque.biohub.aplicacion.hallazgos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;

/** Datos de entrada y salida del módulo de hallazgos (capas cerradas, ADR-02). */
public final class VistasHallazgos {

    private VistasHallazgos() {
    }

    /** Lo que envía el investigador. La coordenada exacta es obligatoria. */
    public record NuevoHallazgo(Long especieId, LocalDate fechaObservacion, String departamento,
                                Map<String, Object> valores, NuevaCoordenada coordenada) {
    }

    public record NuevaCoordenada(Double latitud, Double longitud, Integer precisionMetros) {
    }

    /** Lo que se devuelve. NUNCA incluye la coordenada exacta (ADR-06). */
    public record VistaHallazgo(long id, long especieId, int versionFicha, LocalDate fechaObservacion,
                                String departamento, Map<String, Object> valores, long investigadorId,
                                Instant registradoEn) {
        static VistaHallazgo de(Hallazgo h) {
            return new VistaHallazgo(h.id(), h.especieId(), h.versionFicha(), h.fechaObservacion(),
                    h.departamento(), h.valores(), h.investigadorId(), h.registradoEn());
        }
    }
}
