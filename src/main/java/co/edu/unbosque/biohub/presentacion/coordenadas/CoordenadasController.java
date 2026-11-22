package co.edu.unbosque.biohub.presentacion.coordenadas;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.autorizacion.AccesoCoordenadasDenegadoException;
import co.edu.unbosque.biohub.aplicacion.autorizacion.AutorizacionCoordenadas;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaAcceso;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaCoordenada;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaMapa;
import co.edu.unbosque.biohub.presentacion.comun.ManejadorErrores.RespuestaError;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Coordenadas exactas (HU-08).
 *
 * Los dos endpoints de coordenadas NO llevan @PreAuthorize: basta con haber iniciado
 * sesión, y quién puede ver qué lo decide ÚNICAMENTE AutorizacionCoordenadas (RNF-06).
 * Si la regla también estuviera aquí, habría dos lugares que mantener iguales.
 *
 * Al componente solo se le pasa el ID del usuario: el rol lo busca él en la base.
 */
@RestController
@RequestMapping("/api/cientifico")
@Tag(name = "Coordenadas protegidas")
public class CoordenadasController {

    private final AutorizacionCoordenadas autorizacion;

    public CoordenadasController(AutorizacionCoordenadas autorizacion) {
        this.autorizacion = autorizacion;
    }

    @GetMapping("/hallazgos/{hallazgoId}/coordenada")
    @Operation(summary = "Coordenada exacta de un hallazgo",
               description = "Especie en peligro: solo alto rango. Fuera de peligro: cualquier rol científico. Cada consulta queda registrada.")
    public VistaCoordenada deHallazgo(@PathVariable long hallazgoId, @AuthenticationPrincipal Jwt token) {
        return autorizacion.coordenadaDe(hallazgoId, UsuarioActual.de(token).id());
    }

    @GetMapping("/especies/{especieId}/coordenadas")
    @Operation(summary = "Puntos exactos de una especie, para el mapa",
               description = "Misma regla que la coordenada de un hallazgo. Devuelve como máximo los 500 hallazgos más recientes.")
    public VistaMapa deEspecie(@PathVariable long especieId, @AuthenticationPrincipal Jwt token) {
        return autorizacion.mapaDeEspecie(especieId, UsuarioActual.de(token).id());
    }

    @GetMapping("/coordenadas/accesos")
    @PreAuthorize("hasRole('AUDITOR_CIENTIFICO')")
    @Operation(summary = "Registro de accesos a coordenadas",
               description = "Solo el auditor. Muestra quién consultó, qué se decidió y por qué. No contiene coordenadas.")
    public List<VistaAcceso> accesos(@RequestParam(defaultValue = "false") boolean soloNegados,
                                     @RequestParam(defaultValue = "50") int limite) {
        return autorizacion.accesosRecientes(soloNegados, limite);
    }

    /** Acceso negado por la regla: 403 con la explicación (el intento ya quedó registrado). */
    @ExceptionHandler(AccesoCoordenadasDenegadoException.class)
    ResponseEntity<RespuestaError> negado(AccesoCoordenadasDenegadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new RespuestaError(e.getMessage(), List.of()));
    }
}
