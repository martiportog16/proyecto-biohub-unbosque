package co.edu.unbosque.biohub.dominio.fichas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Una especie adopta la ficha de su grupo taxonómico (RF-02, HU-02 criterios 1 y 4).
 * Lógica pura (S20): sin base de datos.
 */
class HerenciaDeFichaTest {

    private static DefinicionAtributo numero(String nombre, boolean obligatorio) {
        return new DefinicionAtributo(nombre, TipoDato.NUMERO, obligatorio, true, List.of(), null);
    }

    private static DefinicionAtributo texto(String nombre) {
        return new DefinicionAtributo(nombre, TipoDato.TEXTO, false, true, List.of(), null);
    }

    /** Grupo 7 "Anfibios", versión 2: longitud y color_piel. */
    private final FichaTaxonomica fichaDelGrupo =
            FichaTaxonomica.inicial(7L, List.of(numero("longitud", true))).conAtributo(texto("color_piel"));

    @Test
    void laEspecieRecibeLosAtributosDelGrupoEnUnaVersionNueva() {
        FichaTaxonomica especie = FichaTaxonomica.inicial(40L, List.of());

        FichaTaxonomica adoptada = especie.adoptar(fichaDelGrupo);

        assertThat(adoptada.especieId()).as("sigue siendo la ficha de la especie").isEqualTo(40L);
        assertThat(adoptada.version()).isEqualTo(2);
        assertThat(adoptada.activos()).extracting(DefinicionAtributo::nombre).containsExactly("longitud", "color_piel");
    }

    @Test
    void losAtributosPropiosQueElGrupoNoTieneQuedanRetiradosNoBorrados() {
        // La especie ya tenía "peso". Sus hallazgos viejos conservan ese valor (RNF-01).
        FichaTaxonomica especie = FichaTaxonomica.inicial(40L, List.of(numero("peso", true)));

        FichaTaxonomica adoptada = especie.adoptar(fichaDelGrupo);

        assertThat(adoptada.buscar("peso")).hasValueSatisfying(a -> assertThat(a.activo()).isFalse());
        assertThat(adoptada.activos()).extracting(DefinicionAtributo::nombre).doesNotContain("peso");
    }

    @Test
    void siUnNombreCoincideMandaLaDefinicionDelGrupo() {
        // En la especie "longitud" era opcional; en el grupo es obligatoria
        FichaTaxonomica especie = FichaTaxonomica.inicial(40L, List.of(numero("longitud", false)));

        FichaTaxonomica adoptada = especie.adoptar(fichaDelGrupo);

        assertThat(adoptada.buscar("longitud")).hasValueSatisfying(a -> assertThat(a.obligatorio()).isTrue());
        assertThat(adoptada.atributos()).extracting(DefinicionAtributo::nombre).containsOnlyOnce("longitud");
    }

    @Test
    void unAtributoRetiradoEnElGrupoLlegaRetiradoALaEspecie() {
        FichaTaxonomica grupoSinColor = fichaDelGrupo.sinAtributo("color_piel");
        FichaTaxonomica especie = FichaTaxonomica.inicial(40L, List.of()).adoptar(fichaDelGrupo);

        FichaTaxonomica adoptada = especie.adoptar(grupoSinColor);

        assertThat(adoptada.version()).isEqualTo(3);
        assertThat(adoptada.buscar("color_piel")).hasValueSatisfying(a -> assertThat(a.activo()).isFalse());
    }

    @Test
    void adoptarNoCambiaLaFichaOriginalDeLaEspecie() {
        FichaTaxonomica especie = FichaTaxonomica.inicial(40L, List.of(numero("peso", true)));

        especie.adoptar(fichaDelGrupo);

        assertThat(especie.version()).isEqualTo(1);
        assertThat(especie.activos()).extracting(DefinicionAtributo::nombre).containsExactly("peso");
    }

    @Test
    void soloRecibeCambiosLaEspecieQueEstaEnEseGrupoYHereda() {
        assertThat(new MembresiaGrupo(40L, 7L, true).recibeCambiosDe(7L)).isTrue();
        assertThat(new MembresiaGrupo(40L, 7L, false).recibeCambiosDe(7L)).as("ficha propia").isFalse();
        assertThat(new MembresiaGrupo(40L, 8L, true).recibeCambiosDe(7L)).as("otro grupo").isFalse();
        assertThat(new MembresiaGrupo(40L, null, true).recibeCambiosDe(7L)).as("sin grupo").isFalse();
    }
}
