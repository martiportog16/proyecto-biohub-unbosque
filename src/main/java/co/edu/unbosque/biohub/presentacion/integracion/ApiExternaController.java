package co.edu.unbosque.biohub.presentacion.integracion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario.ConsultaInventario;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario.EspecieCatalogo;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario.PaginaInventario;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Permitido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API externa para universidades y ONG, contrato estable /v1 (HU-09, RNF-08, T-21).
 *
 * Todas las rutas pasan antes por FiltroApiKey (401 sin llave, 429 al pasarse del límite).
 * Los nombres de las rutas y de los campos son el CONTRATO: no se cambian dentro de /v1.
 * Un cambio incompatible iría en /v2, dejando /v1 funcionando.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API externa v1", description = "Requiere el encabezado X-API-Key")
@SecurityRequirement(name = "apiKey")
public class ApiExternaController {

    private final ServicioInventario inventario;

    public ApiExternaController(ServicioInventario inventario) {
        this.inventario = inventario;
    }

    @GetMapping("/estado")
    @Operation(summary = "Comprobar la conexión y ver el límite del consumidor")
    public Map<String, Object> estado(@RequestAttribute(FiltroApiKey.ATRIBUTO_CONSUMIDOR) Permitido consumidor) {
        return Map.of(
                "consumidor", consumidor.consumidor(),
                "limitePorMinuto", consumidor.limite(),
                "peticionesRestantesEsteMinuto", consumidor.restantes());
    }

    @GetMapping("/especies")
    @Operation(summary = "Catálogo de especies")
    public List<EspecieCatalogo> especies() {
        return inventario.especies();
    }

    @GetMapping("/hallazgos")
    @Operation(summary = "Inventario de hallazgos, de a 50",
               description = "Especie, fecha, departamento y atributos públicos. Nunca coordenadas. "
                       + "Para la página siguiente, envíe despuesDe con el valor de 'siguiente'. "
                       + "Si 'siguiente' es null, no hay más páginas. Fechas en formato AAAA-MM-DD.")
    public PaginaInventario hallazgos(
            @RequestParam(required = false) Long especieId,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long despuesDe) {
        return inventario.pagina(new ConsultaInventario(especieId, departamento, desde, hasta), despuesDe);
    }
}
