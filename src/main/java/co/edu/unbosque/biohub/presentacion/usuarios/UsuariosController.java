package co.edu.unbosque.biohub.presentacion.usuarios;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.usuarios.ServicioUsuarios;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeClave;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeEstado;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeRol;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.NuevoUsuario;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.UsuarioCreado;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.VistaCambio;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.VistaUsuario;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Gestión de usuarios (P-28, S-01).
 * El administrador de la plataforma crea usuarios, cambia roles y los desactiva.
 * El director científico solo da o quita el rol de alto rango.
 * Aquí se filtra por rol a grandes rasgos; el caso a caso lo decide el dominio
 * (ReglasGestionUsuarios) y responde 403 con el motivo.
 */
@RestController
@RequestMapping("/api/cientifico/usuarios")
@Tag(name = "Usuarios")
public class UsuariosController {

    private static final String PLATAFORMA = "hasRole('ADMINISTRADOR_PLATAFORMA')";
    private static final String PLATAFORMA_O_DIRECTOR = "hasAnyRole('ADMINISTRADOR_PLATAFORMA','DIRECTOR_CIENTIFICO')";

    private final ServicioUsuarios servicio;

    public UsuariosController(ServicioUsuarios servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize(PLATAFORMA_O_DIRECTOR)
    @Operation(summary = "Listar usuarios", description = "El director solo ve al personal científico.")
    public List<VistaUsuario> listar(@AuthenticationPrincipal Jwt token) {
        return servicio.listar(UsuarioActual.de(token).id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PLATAFORMA)
    @Operation(summary = "Crear un usuario",
            description = "Devuelve una clave temporal que se muestra UNA sola vez. El alto rango no se da aquí: lo da un director (S-01).")
    public UsuarioCreado crear(@RequestBody NuevoUsuario datos, @AuthenticationPrincipal Jwt token) {
        return servicio.crear(datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{id}")
    @PreAuthorize(PLATAFORMA_O_DIRECTOR)
    @Operation(summary = "Ver un usuario")
    public VistaUsuario detalle(@PathVariable long id, @AuthenticationPrincipal Jwt token) {
        return servicio.detalle(id, UsuarioActual.de(token).id());
    }

    @PutMapping("/{id}/rol")
    @PreAuthorize(PLATAFORMA_O_DIRECTOR)
    @Operation(summary = "Cambiar el rol", description = "Con motivo obligatorio. Dar o quitar el alto rango: solo un director científico.")
    public VistaUsuario cambiarRol(@PathVariable long id, @RequestBody CambioDeRol datos, @AuthenticationPrincipal Jwt token) {
        return servicio.cambiarRol(id, datos, UsuarioActual.de(token).id());
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize(PLATAFORMA)
    @Operation(summary = "Activar o desactivar", description = "Un usuario desactivado no puede iniciar sesión. Nunca se borra.")
    public VistaUsuario cambiarEstado(@PathVariable long id, @RequestBody CambioDeEstado datos, @AuthenticationPrincipal Jwt token) {
        return servicio.cambiarEstado(id, datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{id}/cambios")
    @PreAuthorize(PLATAFORMA_O_DIRECTOR)
    @Operation(summary = "Bitácora del usuario", description = "Quién lo creó, cambió su rol o lo desactivó, cuándo y por qué.")
    public List<VistaCambio> cambios(@PathVariable long id, @AuthenticationPrincipal Jwt token) {
        return servicio.cambios(id, UsuarioActual.de(token).id());
    }

    @PutMapping("/yo/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar mi clave", description = "Cualquier usuario, con su clave actual. Mínimo 10 caracteres.")
    public void cambiarMiClave(@RequestBody CambioDeClave datos, @AuthenticationPrincipal Jwt token) {
        servicio.cambiarMiClave(UsuarioActual.de(token).id(), datos);
    }
}
