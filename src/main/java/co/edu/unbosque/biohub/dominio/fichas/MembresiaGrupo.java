package co.edu.unbosque.biohub.dominio.fichas;

/**
 * A qué grupo pertenece una especie y si hereda su ficha.
 *
 * @param grupoId    null si la especie no pertenece a ningún grupo
 * @param heredaFicha true: los cambios de la ficha del grupo se copian a la especie.
 *                    false: la especie tiene su ficha propia (por ejemplo, porque el
 *                    administrador la cambió directamente y se independizó del grupo).
 */
public record MembresiaGrupo(long especieId, Long grupoId, boolean heredaFicha) {

    public boolean recibeCambiosDe(long grupo) {
        return heredaFicha && grupoId != null && grupoId == grupo;
    }
}
