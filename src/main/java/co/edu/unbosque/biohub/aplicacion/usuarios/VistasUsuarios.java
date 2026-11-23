package co.edu.unbosque.biohub.aplicacion.usuarios;

import java.time.Instant;

import co.edu.unbosque.biohub.dominio.usuarios.CambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

/** Lo que entra y sale de la gestión de usuarios hacia la presentación. Nunca sale la clave ni su hash. */
public final class VistasUsuarios {

    private VistasUsuarios() {
    }

    public record VistaUsuario(long id, String correo, String nombre, String rol, boolean activo) {
        static VistaUsuario de(Usuario u) {
            return new VistaUsuario(u.id(), u.correo(), u.nombre(), u.rol().name(), u.activo());
        }
    }

    /** La clave temporal se muestra UNA sola vez: BioHub solo guarda su hash BCrypt. */
    public record UsuarioCreado(VistaUsuario usuario, String claveTemporal) {
    }

    public record VistaCambio(String tipo, String rolAnterior, String rolNuevo, String motivo,
                              long autorId, String autor, Instant realizadoEn) {
        static VistaCambio de(CambioUsuario c) {
            return new VistaCambio(c.tipo().name(), c.rolAnterior() == null ? null : c.rolAnterior().name(),
                    c.rolNuevo() == null ? null : c.rolNuevo().name(), c.motivo(), c.autorId(), c.autorNombre(),
                    c.realizadoEn());
        }
    }

    public record NuevoUsuario(String correo, String nombre, String rol, String motivo) {
    }

    public record CambioDeRol(String rol, String motivo) {
    }

    public record CambioDeEstado(Boolean activo, String motivo) {
    }

    public record CambioDeClave(String claveActual, String claveNueva) {
    }
}
