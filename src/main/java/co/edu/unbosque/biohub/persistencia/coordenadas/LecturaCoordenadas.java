package co.edu.unbosque.biohub.persistencia.coordenadas;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta;

/**
 * ILecturaCoordenadas del diseño (C-3, aprobado).
 *
 * Solo LEE coordenadas exactas, y tiene UN SOLO cliente permitido: el componente
 * de autorización (aplicacion.autorizacion). Ninguna otra clase debe pedirla;
 * una prueba de arquitectura lo vigila (PR-08).
 */
public interface LecturaCoordenadas {

    Optional<UbicacionExacta> deHallazgo(long hallazgoId);

    /** Las ubicaciones de los hallazgos más recientes de la especie, como máximo "limite". */
    List<UbicacionExacta> deEspecie(long especieId, int limite);
}
