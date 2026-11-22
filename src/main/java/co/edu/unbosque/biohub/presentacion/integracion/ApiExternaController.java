package co.edu.unbosque.biohub.presentacion.integracion;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Permitido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API externa para universidades y ONG, contrato estable /v1 (RNF-08, T-21).
 * El paquete 09b agrega aquí la consulta del inventario.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API externa v1", description = "Requiere el encabezado X-API-Key")
@SecurityRequirement(name = "apiKey")
public class ApiExternaController {

    @GetMapping("/estado")
    @Operation(summary = "Comprobar la conexión y ver el límite del consumidor")
    public Map<String, Object> estado(@RequestAttribute(FiltroApiKey.ATRIBUTO_CONSUMIDOR) Permitido consumidor) {
        return Map.of(
                "consumidor", consumidor.consumidor(),
                "limitePorMinuto", consumidor.limite(),
                "peticionesRestantesEsteMinuto", consumidor.restantes());
    }
}
