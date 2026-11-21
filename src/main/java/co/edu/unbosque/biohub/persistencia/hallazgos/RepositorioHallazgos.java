package co.edu.unbosque.biohub.persistencia.hallazgos;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;

/** Acceso a los hallazgos. Ninguna operación devuelve coordenadas. */
public interface RepositorioHallazgos {

    long guardar(Hallazgo hallazgo);

    Optional<Hallazgo> buscar(long id);

    /** Filtros opcionales (null = sin filtro). Devuelve como máximo "limite" hallazgos, los más recientes primero. */
    List<Hallazgo> listar(Long especieId, String departamento, int limite, int desplazamiento);
}
