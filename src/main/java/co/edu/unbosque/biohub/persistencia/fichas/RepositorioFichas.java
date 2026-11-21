package co.edu.unbosque.biohub.persistencia.fichas;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;

/** Acceso a especies y a las versiones de sus fichas. */
public interface RepositorioFichas {

    List<Especie> especies();

    Optional<Especie> especie(long id);

    long crearEspecie(String nombreCientifico, String nombreComun, boolean enPeligro);

    boolean existeNombreCientifico(String nombreCientifico);

    /** La versión más reciente de la ficha de la especie. */
    Optional<FichaTaxonomica> fichaVigente(long especieId);

    /** Una versión puntual (la que usó un hallazgo al registrarse). */
    Optional<FichaTaxonomica> fichaEnVersion(long especieId, int version);

    /** Inserta una versión nueva. Nunca modifica las anteriores. */
    void guardarVersion(FichaTaxonomica ficha, long autorId);
}
