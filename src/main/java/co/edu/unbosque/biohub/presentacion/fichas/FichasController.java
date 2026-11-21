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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.fichas.ServicioFichas;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevaEspecie;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevoAtributo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.VistaEspecie;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.VistaFicha;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Fichas flexibles (HU-02). Solo el administrador científico las modifica (CU-02).
 * Cualquier usuario con sesión puede consultarlas.
 */
@RestController
@RequestMapping("/api/cientifico/especies")
@Tag(name = "Especies y fichas")
public class FichasController {

    private static final String SOLO_ADMIN = "hasRole('ADMINISTRADOR_CIENTIFICO')";

    private final ServicioFichas servicio;

    public FichasController(ServicioFichas servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar especies")
    public List<VistaEspecie> especies() {
        return servicio.especies();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Crear una especie con su ficha inicial (versión 1)")
    public VistaFicha crearEspecie(@RequestBody NuevaEspecie datos, @AuthenticationPrincipal Jwt token) {
        return servicio.crearEspecie(datos, UsuarioActual.de(token).id());
    }

    @GetMapping("/{especieId}/ficha")
    @Operation(summary = "Ficha vigente de una especie (incluye atributos retirados, marcados inactivos)")
    public VistaFicha ficha(@PathVariable long especieId) {
        return servicio.fichaVigente(especieId);
    }

    @PostMapping("/{especieId}/ficha/atributos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Agregar un atributo", description = "Crea una versión nueva de la ficha. No cambia código ni registros.")
    public VistaFicha agregarAtributo(@PathVariable long especieId, @RequestBody NuevoAtributo atributo,
                                      @AuthenticationPrincipal Jwt token) {
        return servicio.agregarAtributo(especieId, atributo, UsuarioActual.de(token).id());
    }

    @DeleteMapping("/{especieId}/ficha/atributos/{nombre}")
    @PreAuthorize(SOLO_ADMIN)
    @Operation(summary = "Retirar un atributo", description = "Lo marca inactivo en una versión nueva. Los valores viejos se conservan.")
    public VistaFicha retirarAtributo(@PathVariable long especieId, @PathVariable String nombre,
                                      @AuthenticationPrincipal Jwt token) {
        return servicio.retirarAtributo(especieId, nombre, UsuarioActual.de(token).id());
    }
}
