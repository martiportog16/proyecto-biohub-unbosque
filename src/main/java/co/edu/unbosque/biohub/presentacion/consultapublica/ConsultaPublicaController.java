package co.edu.unbosque.biohub.presentacion.consultapublica;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.consultapublica.LectorPublico;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaEspeciePublica;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaFichaPublica;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Portal público (HU-04): sin iniciar sesión. Nginx envía /api/publico/** al grupo
 * público, que lee de Redis y de la réplica pública, nunca de la primaria.
 */
@RestController
@RequestMapping("/api/publico/especies")
@Tag(name = "Consulta pública")
public class ConsultaPublicaController {

    private final LectorPublico lector;

    public ConsultaPublicaController(LectorPublico lector) {
        this.lector = lector;
    }

    @GetMapping
    @Operation(summary = "Buscar especies", description = "Por nombre científico o común. Sin mayúsculas ni tildes.")
    public List<VistaEspeciePublica> buscar(@RequestParam(required = false) String buscar) {
        return lector.buscar(buscar);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha pública de una especie",
               description = "Atributos que se registran y en qué departamentos se ha visto. Nunca coordenadas exactas.")
    public VistaFichaPublica ficha(@PathVariable long id) {
        return lector.ficha(id);
    }
}
