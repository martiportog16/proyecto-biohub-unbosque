package co.edu.unbosque.biohub.dominio.fichas.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * PR-15: cada estrategia de validación, por separado, con partición de equivalencia
 * y VALORES LÍMITE (S20): el caso que más defectos revela está justo en el borde.
 */
class ValidadoresTest {

    private static DefinicionAtributo def(TipoDato tipo, String... opciones) {
        return new DefinicionAtributo("atributo", tipo, true, true, List.of(opciones), null);
    }

    @Nested
    class Texto {
        private final ValidadorTexto v = new ValidadorTexto();

        @Test void aceptaTextoNormal() { assertThat(v.validar(def(TipoDato.TEXTO), "ADN-0042")).isEmpty(); }
        @Test void aceptaExactamente500() { assertThat(v.validar(def(TipoDato.TEXTO), "a".repeat(500))).isEmpty(); }
        @Test void rechaza501() { assertThat(v.validar(def(TipoDato.TEXTO), "a".repeat(501))).isPresent(); }
        @Test void rechazaVacio() { assertThat(v.validar(def(TipoDato.TEXTO), "   ")).isPresent(); }
        @Test void rechazaNumero() { assertThat(v.validar(def(TipoDato.TEXTO), 12)).isPresent(); }
    }

    @Nested
    class Numero {
        private final ValidadorNumero v = new ValidadorNumero();

        @Test void aceptaCero() { assertThat(v.validar(def(TipoDato.NUMERO), 0)).isEmpty(); }
        @Test void aceptaNegativoDecimal() { assertThat(v.validar(def(TipoDato.NUMERO), -3.5)).isEmpty(); }
        @Test void rechazaNumeroEnTexto() { assertThat(v.validar(def(TipoDato.NUMERO), "12")).isPresent(); }
        @Test void rechazaInfinito() { assertThat(v.validar(def(TipoDato.NUMERO), Double.POSITIVE_INFINITY)).isPresent(); }
    }

    @Nested
    class Fecha {
        private final ValidadorFecha v = new ValidadorFecha();

        @Test void acepta29DeFebreroBisiesto() { assertThat(v.validar(def(TipoDato.FECHA), "2024-02-29")).isEmpty(); }
        @Test void rechaza29DeFebreroNoBisiesto() { assertThat(v.validar(def(TipoDato.FECHA), "2026-02-29")).isPresent(); }

        @ParameterizedTest
        @ValueSource(strings = {"15/11/2026", "2026-13-01", "ayer", ""})
        void rechazaFormatosInvalidos(String fecha) {
            assertThat(v.validar(def(TipoDato.FECHA), fecha)).isPresent();
        }
    }

    @Nested
    class Lista {
        private final ValidadorLista v = new ValidadorLista();
        private final DefinicionAtributo comportamiento = def(TipoDato.LISTA, "alimentacion", "descanso");

        @Test void aceptaUnaOpcion() { assertThat(v.validar(comportamiento, "descanso")).isEmpty(); }
        @Test void rechazaOtraOpcion() { assertThat(v.validar(comportamiento, "volando")).isPresent(); }
        @Test void distingueMayusculas() { assertThat(v.validar(comportamiento, "Descanso")).isPresent(); }
    }

    @Nested
    class RutaGps {
        private final ValidadorRutaGps v = new ValidadorRutaGps();

        private Map<String, Object> p(double lat, double lon) { return Map.of("lat", lat, "lon", lon); }

        @Test void aceptaDosPuntos() {
            assertThat(v.validar(def(TipoDato.RUTA_GPS), List.of(p(4.6, -74.1), p(4.7, -74.0)))).isEmpty();
        }
        @Test void aceptaLosBordesExactos() {
            assertThat(v.validar(def(TipoDato.RUTA_GPS), List.of(p(90, 180), p(-90, -180)))).isEmpty();
        }
        @Test void rechazaLatitudJustoFueraDelBorde() {
            assertThat(v.validar(def(TipoDato.RUTA_GPS), List.of(p(90.0001, 0), p(0, 0)))).isPresent();
        }
        @Test void rechazaUnSoloPunto() {
            assertThat(v.validar(def(TipoDato.RUTA_GPS), List.of(p(4.6, -74.1)))).isPresent();
        }
        @Test void rechazaPuntoSinLongitud() {
            assertThat(v.validar(def(TipoDato.RUTA_GPS), List.of(Map.of("lat", 1), p(0, 0)))).isPresent();
        }
    }

    @Nested
    class Archivo {
        private final ValidadorArchivo v = new ValidadorArchivo();

        @Test void aceptaRutaConExtension() { assertThat(v.validar(def(TipoDato.ARCHIVO), "grabaciones/canto-031.wav")).isEmpty(); }
        @Test void rechazaSinExtension() { assertThat(v.validar(def(TipoDato.ARCHIVO), "grabaciones/canto")).isPresent(); }
        @Test void rechazaVacio() { assertThat(v.validar(def(TipoDato.ARCHIVO), "")).isPresent(); }
    }

    @Test
    void elCatalogoTieneUnaEstrategiaPorCadaTipo() {
        CatalogoValidadores catalogo = CatalogoValidadores.estandar();
        for (TipoDato tipo : TipoDato.values()) {
            assertThat(catalogo.para(tipo).tipo()).isEqualTo(tipo);
        }
    }
}
