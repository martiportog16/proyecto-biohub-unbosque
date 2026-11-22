package co.edu.unbosque.biohub.persistencia.fichas;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.GrupoTaxonomico;
import co.edu.unbosque.biohub.dominio.fichas.MembresiaGrupo;

/**
 * Grupos taxonómicos y sus fichas (RF-02, HU-02). Va aparte de RepositorioFichas
 * para no cambiar lo que ya usan el registro, la consulta pública y la API externa.
 *
 * La ficha de un grupo se maneja como una FichaTaxonomica cuyo dueño es el grupo.
 */
public interface RepositorioGrupos {

    List<GrupoTaxonomico> grupos();

    Optional<GrupoTaxonomico> grupo(long id);

    boolean existeNombre(String nombre);

    long crear(String nombre, String descripcion);

    Optional<FichaTaxonomica> fichaVigenteDelGrupo(long grupoId);

    void guardarVersionDelGrupo(FichaTaxonomica fichaDelGrupo, long autorId);

    /** Guarda en la especie la copia de la ficha del grupo, con el rastro de qué versión del grupo salió. */
    void guardarVersionHeredada(FichaTaxonomica fichaDeLaEspecie, long grupoId, int versionDelGrupo, long autorId);

    MembresiaGrupo membresia(long especieId);

    /** Especies del grupo, con si heredan o no su ficha. */
    List<MembresiaGrupo> miembros(long grupoId);

    void cambiarMembresia(MembresiaGrupo membresia);
}
