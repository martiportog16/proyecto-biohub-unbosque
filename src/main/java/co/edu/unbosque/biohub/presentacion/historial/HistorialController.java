package co.edu.unbosque.biohub.presentacion.historial;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.historial.ServicioReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.NuevaReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaReclasificacion;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reclasificación e historial de un hallazgo (HU-06, HU-07).
 *
 * No hay PUT ni DELETE: el historial no se edita ni se borra por la API (RNF-05).
 */
@RestController
@RequestMapping("/api/cientifico/hallazgos/{hallazgoId}")
@Tag(name = "Historial")
public class HistorialController {

    private final ServicioReclasificacion reclasificacion;

    public HistorialController(ServicioReclasificacion reclasificacion) {
        this.reclasificacion = reclasificacion;
    }

    @PostMapping("/reclasificaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR_CIENTIFICO')")
    @Operation(summary = "Reclasificar un hallazgo",
               description = "Solo el director científico. El motivo es obligatorio. Queda en el historial con autor, fecha, motivo, especie anterior y nueva.")
    public VistaReclasificacion reclasificar(@PathVariable long hallazgoId, @RequestBody NuevaReclasificacion datos,
                                             @AuthenticationPrincipal Jwt token) {
        return reclasificacion.reclasificar(hallazgoId, datos, UsuarioActual.de(token).id());
    }
}
