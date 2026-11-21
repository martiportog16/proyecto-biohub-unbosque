package co.edu.unbosque.biohub.presentacion;

import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Responde qué rol cumple la instancia que atendió la petición.
 * Sirve para comprobar que Nginx enruta cada ruta al grupo correcto (ADR-04, ADR-09).
 */
@RestController
public class EstadoController {

    private final Environment entorno;

    public EstadoController(Environment entorno) {
        this.entorno = entorno;
    }

    @GetMapping({"/api/publico/estado", "/api/cientifico/estado"})
    public Map<String, Object> estado() {
        String[] perfiles = entorno.getActiveProfiles();
        String rol = perfiles.length > 0 ? perfiles[0] : "sin-perfil";
        return Map.of("sistema", "BioHub", "rol", rol);
    }
}
