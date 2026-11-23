package co.edu.unbosque.biohub.dominio.usuarios;

import java.time.Instant;

/**
 * Una entrada de la bitácora de usuarios: quién cambió qué, cuándo y por qué.
 * Solo se agregan; nunca se editan ni se borran (V11). autorNombre y realizadoEn los
 * pone la base al leer; al crear la entrada van en null.
 */
public record CambioUsuario(long usuarioId, long autorId, String autorNombre, TipoCambioUsuario tipo,
                            Rol rolAnterior, Rol rolNuevo, String motivo, Instant realizadoEn) {

    public static CambioUsuario nuevo(long usuarioId, long autorId, TipoCambioUsuario tipo,
                                      Rol rolAnterior, Rol rolNuevo, String motivo) {
        return new CambioUsuario(usuarioId, autorId, null, tipo, rolAnterior, rolNuevo, motivo.trim(), null);
    }
}
