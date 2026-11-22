package co.edu.unbosque.biohub.aplicacion.integracion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.integracion.AtributosPublicos;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioInventario;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioInventario.FilaInventario;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioInventario.Filtros;

/**
 * El inventario para universidades y ONG (HU-09, P-30).
 *
 * Cada hallazgo sale con: especie, fecha, departamento y atributos públicos.
 * Nunca: coordenadas, ruta GPS, archivos ni datos del investigador.
 * Páginas de 50 (P-30).
 */
@Service
public class ServicioInventario {

    public static final int TAMANO_PAGINA = 50;

    public record EspecieExterna(long id, String nombreCientifico, String nombreComun) {
    }

    public record HallazgoExterno(long id, EspecieExterna especie, LocalDate fechaObservacion, String departamento,
                                  Map<String, Object> atributos) {
    }

    /**
     * @param siguiente el valor de "despuesDe" para pedir la página siguiente; null si no hay más
     */
    public record PaginaInventario(List<HallazgoExterno> hallazgos, int tamanoPagina, Long siguiente) {
    }

    public record EspecieCatalogo(long id, String nombreCientifico, String nombreComun, boolean enPeligro) {
    }

    private final RepositorioInventario inventario;
    private final RepositorioFichas fichas;

    public ServicioInventario(RepositorioInventario inventario, RepositorioFichas fichas) {
        this.inventario = inventario;
        this.fichas = fichas;
    }

    public List<EspecieCatalogo> especies() {
        return fichas.especies().stream()
                .map(e -> new EspecieCatalogo(e.id(), e.nombreCientifico(), e.nombreComun(), e.enPeligro()))
                .toList();
    }

    /** Filtros opcionales del consumidor externo; null = sin filtro. */
    public record ConsultaInventario(Long especieId, String departamento, LocalDate desde, LocalDate hasta) {
    }

    public PaginaInventario pagina(ConsultaInventario consulta, Long despuesDe) {
        Filtros filtros = new Filtros(consulta.especieId(), consulta.departamento(), consulta.desde(), consulta.hasta());
        List<String> errores = new ArrayList<>();
        if (despuesDe != null && despuesDe < 0) {
            errores.add("despuesDe: no puede ser negativo");
        }
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            errores.add("desde: no puede ser posterior a hasta");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("La consulta no es válida", errores);
        }

        // Se pide uno de más para saber si hay otra página sin hacer una segunda consulta.
        List<FilaInventario> filas = inventario.pagina(filtros, despuesDe == null ? 0 : despuesDe, TAMANO_PAGINA + 1);
        boolean hayMas = filas.size() > TAMANO_PAGINA;
        List<FilaInventario> estaPagina = hayMas ? filas.subList(0, TAMANO_PAGINA) : filas;

        // Las fichas se buscan una vez por especie y versión, no una vez por hallazgo.
        Map<String, FichaTaxonomica> fichasUsadas = new HashMap<>();
        List<HallazgoExterno> hallazgos = estaPagina.stream().map(f -> {
            FichaTaxonomica ficha = fichasUsadas.computeIfAbsent(f.fichaEspecieId() + "@" + f.versionFicha(),
                    clave -> fichas.fichaEnVersion(f.fichaEspecieId(), f.versionFicha())
                            .orElseThrow(() -> new IllegalStateException("Falta la ficha de " + clave)));
            return new HallazgoExterno(f.id(), new EspecieExterna(f.especieId(), f.nombreCientifico(), f.nombreComun()),
                    f.fechaObservacion(), f.departamento(), AtributosPublicos.filtrar(ficha, f.valores()));
        }).toList();

        Long siguiente = hayMas ? estaPagina.get(estaPagina.size() - 1).id() : null;
        return new PaginaInventario(hallazgos, TAMANO_PAGINA, siguiente);
    }
}
