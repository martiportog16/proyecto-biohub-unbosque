package co.edu.unbosque.biohub.dominio.fichas;

/**
 * Un grupo taxonómico o categoría (RF-02, HU-02): por ejemplo "Anfibios" o "Aves rapaces".
 * Puede tener su propia ficha, que heredan las especies del grupo que lo elijan.
 */
public record GrupoTaxonomico(long id, String nombre, String descripcion) {
}
