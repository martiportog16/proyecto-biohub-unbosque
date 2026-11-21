package co.edu.unbosque.biohub.presentacion.comun;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace la petición, leído del token JWT.
 * Los controladores lo pasan a la aplicación como datos simples (id y rol), así la
 * aplicación no depende de cómo se autenticó el usuario.
 */
public record UsuarioActual(long id, String correo, String nombre, String rol) {

    public static UsuarioActual de(Jwt token) {
        return new UsuarioActual(
                Long.parseLong(token.getSubject()),
                token.getClaimAsString("correo"),
                token.getClaimAsString("nombre"),
                token.getClaimAsString("rol"));
    }
}
