package co.edu.unbosque.biohub.dominio.fichas;

/**
 * Una especie del inventario. "enPeligro" decide quién puede ver las coordenadas
 * exactas de sus hallazgos (P-24).
 */
public record Especie(Long id, String nombreCientifico, String nombreComun, boolean enPeligro) {
}
