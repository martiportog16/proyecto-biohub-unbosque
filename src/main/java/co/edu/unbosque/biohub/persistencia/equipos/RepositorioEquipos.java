package co.edu.unbosque.biohub.persistencia.equipos;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.equipos.AsignacionEquipo;
import co.edu.unbosque.biohub.dominio.equipos.EquipoCampo;

/** Equipos de campo y su historial de asignaciones (RF-03, RF-04). */
public interface RepositorioEquipos {

    /** Guarda el equipo y su primera asignación. Devuelve el id. */
    long registrar(EquipoCampo equipo, long registradoPor);

    boolean existeCodigo(String codigo);

    Optional<EquipoCampo> buscar(long id);

    Optional<EquipoCampo> buscarPorCodigo(String codigo);

    /** @param responsableId null para todos */
    List<EquipoCampo> listar(Long responsableId);

    /**
     * Cambia el responsable solo si sigue siendo {@code responsableAnterior} (bloqueo optimista)
     * y agrega la entrada al historial. Devuelve false si otro lo cambió primero.
     */
    boolean reasignar(long equipoId, long responsableAnterior, long nuevoResponsable, long asignadoPor, String motivo);

    /** Historial del equipo, del más antiguo al más reciente. */
    List<AsignacionEquipo> asignaciones(long equipoId);
}
