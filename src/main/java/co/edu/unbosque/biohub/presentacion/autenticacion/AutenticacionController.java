package co.edu.unbosque.biohub.presentacion.autenticacion;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.autenticacion.ServicioAutenticacion;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/cientifico")
@Tag(name = "Autenticación")
public class AutenticacionController {

    private final ServicioAutenticacion servicio;

    public AutenticacionController(ServicioAutenticacion servicio) {
        this.servicio = servicio;
    }

    public record SolicitudLogin(@NotBlank @Email String correo, @NotBlank String clave) {
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve un token JWT válido por 60 minutos")
    public ServicioAutenticacion.Sesion iniciarSesion(@Valid @RequestBody SolicitudLogin solicitud) {
        return servicio.iniciarSesion(solicitud.correo(), solicitud.clave());
    }

    @GetMapping("/usuarios/yo")
    @Operation(summary = "Quién soy", description = "Datos del usuario dueño del token")
    public UsuarioActual quienSoy(@AuthenticationPrincipal Jwt token) {
        return UsuarioActual.de(token);
    }
}
