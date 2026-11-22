package co.edu.unbosque.biohub.presentacion.fichas;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.fichas.ServicioGruposTaxonomicos;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevoAtributo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.Asignacion;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.CambioDeGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.NuevoGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.VistaFichaGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.VistaGrupo;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Grupos taxonómicos y sus fichas (RF-02, HU-02). Solo el administrador científico los cambia. */
@RestController
@RequestMapping("/api/cientifico/grupos")
@Tag(name = "Grupos taxonómicos")
public class GruposController {

    private static final String SOLO_ADMIN = "hasRole('ADMINISTRADOR_CIENTIFICO')";

    private final ServicioGruposTaxonomicos servicio;

    public GruposController(ServicioGruposTaxonomicos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar grupos con sus especies")
    public List<VistaGrupo> grupos() {
        return servicio.grupos();
    }

    @GetMapping("/{grupoId}")
    @Operation(summary = "Un grupo con sus especies y si heredan la ficha")
    public VistaGrupo grupo(@PathVariable long grupoId) {
        return servicio.grupo(grupoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Crear un grupo, con o sin ficha inicial")
    public VistaGrupo crear(@RequestBody NuevoGrupo datos, @AuthenticationPrincipal Jwt token) {
        return servicio.crear(datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{grupoId}/ficha")
    @Operation(summary = "Ficha vigente del grupo")
    public VistaFichaGrupo ficha(@PathVariable long grupoId) {
        return servicio.fichaDelGrupo(grupoId);
    }

    @PostMapping("/{grupoId}/ficha/atributos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Agregar un atributo al grupo",
            description = "Crea una versión nueva de la ficha del grupo y la copia a sus especies que la heredan.")
    public CambioDeGrupo agregarAtributo(@PathVariable long grupoId, @RequestBody NuevoAtributo atributo,
                                         @AuthenticationPrincipal Jwt token) {
        return servicio.agregarAtributo(grupoId, atributo, UsuarioActual.de(token).id());
    }

    @DeleteMapping("/{grupoId}/ficha/atributos/{nombre}")
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Retirar un atributo del grupo", description = "Lo marca inactivo y lo copia a sus especies.")
    public CambioDeGrupo retirarAtributo(@PathVariable long grupoId, @PathVariable String nombre,
                                         @AuthenticationPrincipal Jwt token) {
        return servicio.retirarAtributo(grupoId, nombre, UsuarioActual.de(token).id());
    }

    @PutMapping("/{grupoId}/especies")
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Meter una especie al grupo", description = "Con heredarFicha=true recibe la ficha del grupo.")
    public VistaGrupo asignar(@PathVariable long grupoId, @RequestBody Asignacion asignacion,
                              @AuthenticationPrincipal Jwt token) {
        return servicio.asignar(grupoId, asignacion, UsuarioActual.de(token).id());
    }
}
