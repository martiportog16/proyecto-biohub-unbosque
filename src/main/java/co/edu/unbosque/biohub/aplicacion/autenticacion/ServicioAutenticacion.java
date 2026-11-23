package co.edu.unbosque.biohub.aplicacion.autenticacion;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.autenticacion.ContadorIntentosFallidos;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Caso de uso: iniciar sesión (P-28).
 * Verifica correo y clave contra el hash BCrypt y entrega un token con el rol.
 *
 * Contra la fuerza bruta (T-17): después de 10 claves equivocadas seguidas para un mismo
 * correo, ese correo queda bloqueado 15 minutos, sin siquiera revisar la clave (así un
 * ataque tampoco gasta CPU en BCrypt). Además, Nginx limita los intentos por IP.
 */
@Service
public class ServicioAutenticacion {

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final EmisorDeTokens emisor;
    private final ContadorIntentosFallidos intentos;

    static final int MAXIMO_FALLOS = 10;
    static final long SEGUNDOS_DE_BLOQUEO = 15 * 60;

    public ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder codificador, EmisorDeTokens emisor,
                                 ContadorIntentosFallidos intentos) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
        this.intentos = intentos;
    }

    public Sesion iniciarSesion(String correo, String clave) {
        if (intentos.fallos(correo) >= MAXIMO_FALLOS) {
            throw new DemasiadosIntentosException(SEGUNDOS_DE_BLOQUEO);
        }
        Optional<Usuario> encontrado = usuarios.buscarPorCorreo(correo)
                .filter(Usuario::puedeIniciarSesion)
                .filter(u -> codificador.matches(clave, u.claveHash()));
        if (encontrado.isEmpty()) {
            intentos.registrarFallo(correo);
            throw new CredencialesInvalidasException();
        }
        intentos.limpiar(correo);
        Usuario usuario = encontrado.get();

        EmisorDeTokens.TokenEmitido token = emisor.emitir(usuario);
        return new Sesion(token.valor(), token.expiraEnSegundos(), usuario.nombre(), usuario.rol().name());
    }

    public record Sesion(String token, long expiraEnSegundos, String nombre, String rol) {
    }
}
