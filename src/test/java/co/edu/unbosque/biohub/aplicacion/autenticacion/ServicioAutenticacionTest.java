package co.edu.unbosque.biohub.aplicacion.autenticacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Pruebas unitarias del inicio de sesión (S20): rápidas, sin base de datos.
 * El repositorio es un FAKE en memoria y el emisor de tokens es un STUB.
 */
class ServicioAutenticacionTest {

    private static final PasswordEncoder BCRYPT = new BCryptPasswordEncoder(4); // costo bajo: prueba rápida
    private static final String CLAVE = "BioHub2026!";

    private ServicioAutenticacion servicio;

    @BeforeEach
    void preparar() {
        Usuario activa = new Usuario(1L, "ana@biohub.co", "Ana", BCRYPT.encode(CLAVE), Rol.INVESTIGADOR, true);
        Usuario inactivo = new Usuario(2L, "luis@biohub.co", "Luis", BCRYPT.encode(CLAVE), Rol.AUDITOR_CIENTIFICO, false);

        RepositorioUsuarios fake = new RepositorioUsuarios() {
            @Override
            public Optional<Usuario> buscarPorCorreo(String correo) {
                if (correo.equals(activa.correo())) return Optional.of(activa);
                if (correo.equals(inactivo.correo())) return Optional.of(inactivo);
                return Optional.empty();
            }

            @Override
            public Optional<Usuario> buscarPorId(long id) {
                return Optional.empty();
            }
        };
        EmisorDeTokens stub = u -> new EmisorDeTokens.TokenEmitido("token-de-" + u.id(), 3600);

        servicio = new ServicioAutenticacion(fake, BCRYPT, stub);
    }

    @Test
    void conCorreoYClaveCorrectosEntregaTokenYRol() {
        // Act
        ServicioAutenticacion.Sesion sesion = servicio.iniciarSesion("ana@biohub.co", CLAVE);

        // Assert
        assertThat(sesion.token()).isEqualTo("token-de-1");
        assertThat(sesion.rol()).isEqualTo("INVESTIGADOR");
        assertThat(sesion.expiraEnSegundos()).isEqualTo(3600);
    }

    @Test
    void conClaveIncorrectaRechaza() {
        assertThatThrownBy(() -> servicio.iniciarSesion("ana@biohub.co", "otra"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void conCorreoInexistenteRechazaConElMismoMensaje() {
        assertThatThrownBy(() -> servicio.iniciarSesion("nadie@biohub.co", CLAVE))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Correo o clave incorrectos");
    }

    @Test
    void unUsuarioInactivoNoPuedeIniciarSesion() {
        assertThatThrownBy(() -> servicio.iniciarSesion("luis@biohub.co", CLAVE))
                .isInstanceOf(CredencialesInvalidasException.class);
    }
}
