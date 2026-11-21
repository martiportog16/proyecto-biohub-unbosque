package co.edu.unbosque.biohub.dominio.fichas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;

/** Reglas de la ficha flexible, sin base de datos (S20: rápidas y aisladas). */
class FichaTaxonomicaTest {

    private final CatalogoValidadores catalogo = CatalogoValidadores.estandar();

    private final FichaTaxonomica ficha = FichaTaxonomica.inicial(1L, List.of(
            new DefinicionAtributo("comportamiento", TipoDato.LISTA, true, true, List.of("alimentacion", "descanso"), null),
            new DefinicionAtributo("temperatura", TipoDato.NUMERO, false, true, List.of(), "°C")));

    @Test
    void laFichaInicialEsLaVersion1() {
        assertThat(ficha.version()).isEqualTo(1);
        assertThat(ficha.activos()).hasSize(2);
    }

    @Test
    void valoresCorrectosNoTienenErrores() {
        assertThat(ficha.validar(Map.of("comportamiento", "descanso", "temperatura", 18.5), catalogo)).isEmpty();
    }

    @Test
    void reuneTodosLosErroresAntesDeRechazar() {
        Map<String, Object> valores = Map.of("temperatura", "caliente", "color", "rojo");

        List<String> errores = ficha.validar(valores, catalogo);

        assertThat(errores).hasSize(3).anyMatch(e -> e.startsWith("comportamiento: es obligatorio"))
                .anyMatch(e -> e.startsWith("temperatura:"))
                .anyMatch(e -> e.startsWith("color: no es un atributo"));
    }

    @Test
    void unAtributoOpcionalPuedeFaltar() {
        assertThat(ficha.validar(Map.of("comportamiento", "alimentacion"), catalogo)).isEmpty();
    }

    @Test
    void agregarUnAtributoCreaUnaVersionNuevaSinTocarLaAnterior() {
        FichaTaxonomica v2 = ficha.conAtributo(
                new DefinicionAtributo("muestra_adn", TipoDato.TEXTO, false, true, List.of(), null));

        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.activos()).hasSize(3);
        assertThat(ficha.activos()).hasSize(2); // la versión 1 sigue igual (inmutable)
    }

    @Test
    void retirarUnAtributoLoMarcaInactivoYNoLoBorra() {
        FichaTaxonomica v2 = ficha.sinAtributo("temperatura");

        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.buscar("temperatura")).get().extracting(DefinicionAtributo::activo).isEqualTo(false);
        assertThat(v2.validar(Map.of("comportamiento", "descanso", "temperatura", 20), catalogo))
                .containsExactly("temperatura: el atributo fue retirado y ya no se registra");
    }

    @Test
    void noSePuedeRepetirElNombreNiDeUnAtributoRetirado() {
        FichaTaxonomica v2 = ficha.sinAtributo("temperatura");
        DefinicionAtributo repetido = new DefinicionAtributo("temperatura", TipoDato.NUMERO, false, true, List.of(), null);

        assertThatThrownBy(() -> v2.conAtributo(repetido)).isInstanceOf(ReglaDeFichaException.class);
    }

    @Test
    void unaListaNecesitaAlMenosDosOpciones() {
        DefinicionAtributo mala = new DefinicionAtributo("sexo", TipoDato.LISTA, false, true, List.of("macho"), null);

        assertThatThrownBy(() -> ficha.conAtributo(mala))
                .isInstanceOf(ReglaDeFichaException.class)
                .satisfies(e -> assertThat(((ReglaDeFichaException) e).getErrores())
                        .anyMatch(m -> m.contains("al menos 2 opciones")));
    }

    @Test
    void diezModificacionesSonDiezVersionesNuevas() {
        FichaTaxonomica actual = ficha;
        for (int i = 1; i <= 5; i++) {
            actual = actual.conAtributo(new DefinicionAtributo("dato_" + i, TipoDato.TEXTO, false, true, List.of(), null));
            actual = actual.sinAtributo("dato_" + i);
        }
        assertThat(actual.version()).isEqualTo(11);
        Map<String, Object> valoresViejos = new HashMap<>(Map.of("comportamiento", "descanso"));
        assertThat(actual.validar(valoresViejos, catalogo)).isEmpty();
    }
}
