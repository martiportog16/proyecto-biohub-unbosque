package co.edu.unbosque.biohub.presentacion.historial;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.historial.ServicioHistorial;
import co.edu.unbosque.biohub.aplicacion.historial.ServicioReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.NuevaReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaComparacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaHistorial;
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

    /** Quién puede LEER el historial: el director (que reclasifica) y el auditor (HU-07). */
    private static final String PUEDE_LEER = "hasAnyRole('DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO')";

    private final ServicioReclasificacion reclasificacion;
    private final ServicioHistorial historial;

    public HistorialController(ServicioReclasificacion reclasificacion, ServicioHistorial historial) {
        this.reclasificacion = reclasificacion;
        this.historial = historial;
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

    @GetMapping("/historial")
    @PreAuthorize(PUEDE_LEER)
    @Operation(summary = "Historial completo del hallazgo",
               description = "Todas las reclasificaciones en orden cronológico, cada una con autor, fecha, motivo, especie anterior y nueva.")
    public VistaHistorial historial(@PathVariable long hallazgoId) {
        return historial.historial(hallazgoId);
    }

    @GetMapping("/historial/comparacion")
    @PreAuthorize(PUEDE_LEER)
    @Operation(summary = "Comparar el estado en una fecha con el actual",
               description = "fecha en formato AAAA-MM-DD. Usa el estado al final de ese día, hora de Colombia.")
    public VistaComparacion comparar(@PathVariable long hallazgoId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return historial.comparar(hallazgoId, fecha);
    }
}
