package co.edu.unbosque.biohub.dominio.coordenadas;

import java.time.LocalDate;

import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;

/**
 * La ubicación exacta de un hallazgo, lista para ponerla en un mapa.
 * Solo la entrega LecturaCoordenadas, y solo a la autorización (C-3).
 */
public record UbicacionExacta(long hallazgoId, String departamento, LocalDate fechaObservacion,
                              Coordenada coordenada) {
}
