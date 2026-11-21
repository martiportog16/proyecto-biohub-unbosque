package co.edu.unbosque.biohub.presentacion.hallazgos;

import java.util.List;

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

import co.edu.unbosque.biohub.aplicacion.hallazgos.ServicioRegistroHallazgos;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.NuevoHallazgo;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.VistaHallazgo;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registro y consulta de hallazgos (HU-01, HU-05). Solo el investigador registra (CU-01).
 * Ninguna respuesta de este controlador incluye coordenadas exactas.
 */
@RestController
@RequestMapping("/api/cientifico/hallazgos")
@Tag(name = "Hallazgos")
public class HallazgosController {

    private final ServicioRegistroHallazgos servicio;

    public HallazgosController(ServicioRegistroHallazgos servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('INVESTIGADOR')")
    @Operation(summary = "Registrar un hallazgo",
               description = "Valida los valores contra la ficha vigente. Si algo falla responde 422 con TODOS los errores y no guarda nada.")
    public VistaHallazgo registrar(@RequestBody NuevoHallazgo datos, @AuthenticationPrincipal Jwt token) {
        return servicio.registrar(datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un hallazgo (sin coordenadas exactas)")
    public VistaHallazgo consultar(@PathVariable long id) {
        return servicio.consultar(id);
    }

    @GetMapping
    @Operation(summary = "Listar hallazgos", description = "Filtros opcionales. 50 por página, los más recientes primero.")
    public List<VistaHallazgo> listar(@RequestParam(required = false) Long especieId,
                                      @RequestParam(required = false) String departamento,
                                      @RequestParam(defaultValue = "0") int pagina) {
        return servicio.listar(especieId, departamento, pagina);
    }
}
