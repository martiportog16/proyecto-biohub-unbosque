package co.edu.unbosque.biohub.dominio.historial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Una reclasificación de un hallazgo (HU-06, RF-06, RF-07).
 *
 * Guarda los 5 datos que pide el criterio 3 de HU-06: autor, fecha, motivo,
 * valor anterior y valor nuevo. Es un record: una vez creada no se puede cambiar.
 */
public record Reclasificacion(
        Long id,
        long hallazgoId,
        long especieAnteriorId,
        long especieNuevaId,
        String motivo,
        long autorId,
        Instant realizadaEn) {

    /** Un motivo de una o dos palabras ("error", "x") no explica nada a un auditor. */
    public static final int LARGO_MINIMO_MOTIVO = 10;

    /**
     * Reglas de una reclasificación nueva. Reúne todos los errores, como el registro
     * de hallazgos, para que el director los corrija de una vez.
     */
    public static List<String> errores(long especieActualId, Long especieNuevaId, String motivo) {
        List<String> errores = new ArrayList<>();
        if (especieNuevaId == null) {
            errores.add("especieNuevaId: es obligatoria");
        } else if (especieNuevaId == especieActualId) {
            errores.add("especieNuevaId: el hallazgo ya está clasificado en esa especie");
        }
        if (motivo == null || motivo.isBlank()) {
            errores.add("motivo: es obligatorio");
        } else if (motivo.strip().length() < LARGO_MINIMO_MOTIVO) {
            errores.add("motivo: debe explicar la nueva evidencia (mínimo " + LARGO_MINIMO_MOTIVO + " caracteres)");
        }
        return errores;
    }
}
