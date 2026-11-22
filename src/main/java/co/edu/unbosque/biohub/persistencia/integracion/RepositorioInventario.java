package co.edu.unbosque.biohub.persistencia.integracion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lectura del inventario para consumidores externos (HU-09).
 * Corre en el grupo público, contra la réplica pública: nunca toca la primaria.
 * Solo lee tablas del esquema "public": app_publico no puede entrar a "protegido".
 */
public interface RepositorioInventario {

    /** Una fila del inventario. Trae TODOS los valores; el filtro de atributos públicos va en la aplicación. */
    record FilaInventario(long id, long especieId, String nombreCientifico, String nombreComun, LocalDate fechaObservacion,
                          String departamento, long fichaEspecieId, int versionFicha, Map<String, Object> valores) {
    }

    record Filtros(Long especieId, String departamento, LocalDate desde, LocalDate hasta) {
    }

    /**
     * Hasta "limite" hallazgos con id mayor que "despuesDe", en orden de id.
     * Paginación por cursor: la página siguiente empieza donde terminó la anterior,
     * aunque mientras tanto se registren hallazgos nuevos.
     */
    List<FilaInventario> pagina(Filtros filtros, long despuesDe, int limite);
}
