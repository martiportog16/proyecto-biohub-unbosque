package co.edu.unbosque.biohub.aplicacion.equipos;

import java.time.Instant;

/** Lo que entra y sale de los equipos de campo hacia la presentación. */
public final class VistasEquipos {

    private VistasEquipos() {
    }

    public record VistaPersona(long id, String nombre, String correo, String rol) {
    }

    /** RF-04: el equipo siempre trae a su responsable actual. */
    public record VistaEquipo(long id, String codigo, String tipo, String descripcion, VistaPersona responsable) {
    }

    public record VistaAsignacion(long responsableId, String responsable, long asignadoPor,
                                  Instant asignadoEn, String motivo) {
    }

    public record NuevoEquipo(String codigo, String tipo, String descripcion, Long responsableId) {
    }

    public record Reasignacion(Long responsableId, String motivo) {
    }
}
