package co.edu.unbosque.biohub.presentacion.equipos;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.equipos.ServicioEquipos;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.NuevoEquipo;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.Reasignacion;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaAsignacion;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaEquipo;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaPersona;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Equipos de campo (RF-03, RF-04, HU-03).
 * Según el diagrama de casos de uso, el único actor de CU-03 (asociar un equipo a su
 * responsable) y de CU-04 (consultar quién lo tiene) es el investigador.
 */
@RestController
@RequestMapping("/api/cientifico/equipos")
@Tag(name = "Equipos de campo")
@PreAuthorize(EquiposController.INVESTIGADOR)
public class EquiposController {

    static final String INVESTIGADOR = "hasRole('INVESTIGADOR')";

    private final ServicioEquipos servicio;

    public EquiposController(ServicioEquipos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar equipos con su responsable", description = "Con responsableId, solo los de esa persona.")
    public List<VistaEquipo> listar(@RequestParam(required = false) Long responsableId) {
        return servicio.listar(responsableId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un equipo con su responsable (CU-03)",
            description = "Sin responsable no se registra (HU-03, criterio 4).")
    public VistaEquipo registrar(@RequestBody NuevoEquipo datos, @AuthenticationPrincipal Jwt token) {
        return servicio.registrar(datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Quién tiene el equipo (CU-04, RF-04)")
    public VistaEquipo detalle(@PathVariable long id) {
        return servicio.detalle(id);
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Buscar un equipo por su código, por ejemplo DRON-007")
    public VistaEquipo porCodigo(@PathVariable String codigo) {
        return servicio.porCodigo(codigo);
    }

    @PutMapping("/{id}/responsable")
    @Operation(summary = "Pasar el equipo a otro responsable", description = "El motivo es obligatorio y queda en el historial.")
    public VistaEquipo reasignar(@PathVariable long id, @RequestBody Reasignacion datos,
                                 @AuthenticationPrincipal Jwt token) {
        return servicio.reasignar(id, datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{id}/asignaciones")
    @Operation(summary = "Historial de responsables del equipo")
    public List<VistaAsignacion> asignaciones(@PathVariable long id) {
        return servicio.asignaciones(id);
    }

    @GetMapping("/responsables-posibles")
    @Operation(summary = "Investigadores activos que pueden quedar a cargo de un equipo")
    public List<VistaPersona> responsablesPosibles() {
        return servicio.responsablesPosibles();
    }
}
