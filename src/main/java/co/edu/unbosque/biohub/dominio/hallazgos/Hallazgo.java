package co.edu.unbosque.biohub.dominio.hallazgos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Un hallazgo registrado por un investigador (RF-01).
 *
 * No navega hacia su coordenada exacta (ADR-06): cargar un hallazgo NUNCA trae
 * coordenadas. Para verlas hay que pasar por el componente de autorización.
 */
public record Hallazgo(
        Long id,
        long especieId,
        long fichaEspecieId,
        int versionFicha,
        Map<String, Object> valores,
        LocalDate fechaObservacion,
        String departamento,
        long investigadorId,
        Instant registradoEn) {

    public Hallazgo {
        valores = Map.copyOf(valores);
    }

    /**
     * Reglas del hallazgo que no dependen de la ficha.
     * "hoy" llega desde afuera (un reloj inyectado) para que la regla se pueda probar
     * con cualquier fecha (S21: stub del reloj).
     */
    public static List<String> erroresDeDatos(LocalDate fechaObservacion, String departamento, LocalDate hoy) {
        List<String> errores = new ArrayList<>();
        if (fechaObservacion == null) {
            errores.add("fechaObservacion: es obligatoria");
        } else if (fechaObservacion.isAfter(hoy)) {
            errores.add("fechaObservacion: no puede ser una fecha futura");
        }
        if (!Departamentos.esValido(departamento)) {
            errores.add("departamento: debe ser un departamento de Colombia (ej. Cundinamarca, Bogotá D.C.)");
        }
        return errores;
    }
}
