package co.edu.unbosque.biohub.aplicacion.hallazgos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import co.edu.unbosque.biohub.dominio.fichas.DatosDeUbicacion;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
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

    /**
     * Lo que se devuelve. NUNCA incluye la coordenada exacta (ADR-06) ni datos de ubicación
     * de la ficha (ruta GPS, archivos): esos solo salen por la autorización de coordenadas.
     * valoresProtegidos dice cuáles se retuvieron, para que la pantalla lo explique.
     */
    public record VistaHallazgo(long id, long especieId, int versionFicha, LocalDate fechaObservacion,
                                String departamento, Map<String, Object> valores, List<String> valoresProtegidos,
                                long investigadorId, Instant registradoEn) {
        static VistaHallazgo de(Hallazgo h, FichaTaxonomica ficha) {
            DatosDeUbicacion.Separados datos = DatosDeUbicacion.separar(ficha, h.valores());
            return new VistaHallazgo(h.id(), h.especieId(), h.versionFicha(), h.fechaObservacion(),
                    h.departamento(), datos.visibles(), datos.nombresProtegidos(), h.investigadorId(), h.registradoEn());
        }
    }
}
