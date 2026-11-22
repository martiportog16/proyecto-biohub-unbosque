package co.edu.unbosque.biohub.presentacion.integracion;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.integracion.ServicioConsumidores;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioConsumidores.CambioConsumidor;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioConsumidores.VistaConsumidor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Administración de consumidores externos (HU-10). Va por el grupo científico
 * porque escribe en la primaria. La API key nunca se muestra ni se cambia por aquí.
 */
@RestController
@RequestMapping("/api/cientifico/consumidores")
@PreAuthorize("hasRole('ADMINISTRADOR_PLATAFORMA')")
@Tag(name = "Consumidores externos")
public class ConsumidoresController {

    private final ServicioConsumidores servicio;

    public ConsumidoresController(ServicioConsumidores servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Consumidores externos y su límite por minuto")
    public List<VistaConsumidor> listar() {
        return servicio.listar();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar el límite o desactivar un consumidor",
               description = "Envíe limitePorMinuto (1 a 10 000), activo, o ambos. Aplica desde la siguiente petición.")
    public VistaConsumidor cambiar(@PathVariable long id, @RequestBody CambioConsumidor cambio) {
        return servicio.cambiar(id, cambio);
    }
}
