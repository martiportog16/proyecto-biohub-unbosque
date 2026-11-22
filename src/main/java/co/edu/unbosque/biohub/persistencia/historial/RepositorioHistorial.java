package co.edu.unbosque.biohub.persistencia.historial;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.historial.Reclasificacion;

/**
 * Acceso al historial de reclasificaciones (RNF-05).
 *
 * A propósito NO tiene métodos para editar ni borrar: lo que no existe en la
 * interfaz no se puede llamar desde ninguna capa.
 */
public interface RepositorioHistorial {

    /**
     * Guarda la reclasificación y cambia la especie actual del hallazgo, juntas.
     * Devuelve el id de la reclasificación, o vacío si el hallazgo ya no estaba en la
     * especie "anterior" (otra persona lo reclasificó al mismo tiempo); en ese caso no guarda nada.
     */
    Optional<Long> registrar(Reclasificacion reclasificacion);

    /** Todas las reclasificaciones del hallazgo, en orden cronológico. */
    List<Reclasificacion> deHallazgo(long hallazgoId);
}
