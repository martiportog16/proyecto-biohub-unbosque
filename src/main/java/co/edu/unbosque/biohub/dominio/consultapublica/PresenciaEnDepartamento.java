package co.edu.unbosque.biohub.dominio.consultapublica;

import java.time.LocalDate;

/**
 * Dónde se ha visto una especie, al nivel que puede ver el público:
 * el DEPARTAMENTO, nunca la coordenada exacta (RF-09, HU-04 criterio 4).
 */
public record PresenciaEnDepartamento(String departamento, long hallazgos, LocalDate ultimaObservacion) {
}
