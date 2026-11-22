package co.edu.unbosque.biohub.aplicacion.consultapublica;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que ve un visitante (HU-04). A propósito NO hay ningún campo de coordenadas
 * ni del investigador: lo que no está en la vista no puede salir por la API.
 */
public final class VistasConsultaPublica {

    private VistasConsultaPublica() {
    }

    public record VistaEspeciePublica(long id, String nombreCientifico, String nombreComun, boolean enPeligro,
                                      long totalHallazgos) {
    }

    public record VistaAtributoPublico(String nombre, String tipo, String unidad) {
    }

    public record VistaDepartamento(String departamento, long hallazgos, LocalDate ultimaObservacion) {
    }

    public record VistaFichaPublica(long id, String nombreCientifico, String nombreComun, boolean enPeligro,
                                    int versionFicha, List<VistaAtributoPublico> atributos, long totalHallazgos,
                                    List<VistaDepartamento> departamentos) {
    }
}
