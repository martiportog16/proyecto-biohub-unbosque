package co.edu.unbosque.biohub.aplicacion.autenticacion;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Caso de uso: iniciar sesión (P-28).
 * Verifica correo y clave contra el hash BCrypt y entrega un token con el rol.
 */
@Service
public class ServicioAutenticacion {

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final EmisorDeTokens emisor;

    public ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder codificador, EmisorDeTokens emisor) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
    }

    public Sesion iniciarSesion(String correo, String clave) {
        Usuario usuario = usuarios.buscarPorCorreo(correo)
                .filter(Usuario::puedeIniciarSesion)
                .filter(u -> codificador.matches(clave, u.claveHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        EmisorDeTokens.TokenEmitido token = emisor.emitir(usuario);
        return new Sesion(token.valor(), token.expiraEnSegundos(), usuario.nombre(), usuario.rol().name());
    }

    public record Sesion(String token, long expiraEnSegundos, String nombre, String rol) {
    }
}
