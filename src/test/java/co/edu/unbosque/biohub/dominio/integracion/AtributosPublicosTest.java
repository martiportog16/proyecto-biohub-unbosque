package co.edu.unbosque.biohub.dominio.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/** Qué valores salen hacia los externos (P-30), sin base de datos. */
class AtributosPublicosTest {

    private static DefinicionAtributo atributo(String nombre, TipoDato tipo) {
        return new DefinicionAtributo(nombre, tipo, false, true, tipo == TipoDato.LISTA ? List.of("macho", "hembra") : List.of(), null);
    }

    private final FichaTaxonomica ficha = FichaTaxonomica.inicial(2L, List.of(
            atributo("ancho_huella", TipoDato.NUMERO),
            atributo("color", TipoDato.TEXTO),
            atributo("fecha_muestra", TipoDato.FECHA),
            atributo("sexo", TipoDato.LISTA),
            atributo("ruta_desplazamiento", TipoDato.RUTA_GPS),
            atributo("foto", TipoDato.ARCHIVO)));

    private final Map<String, Object> valores = Map.of(
            "ancho_huella", 14.5,
            "color", "negro",
            "fecha_muestra", "2026-11-01",
            "sexo", "macho",
            "ruta_desplazamiento", List.of(Map.of("lat", 4.71, "lon", -74.07), Map.of("lat", 4.72, "lon", -74.08)),
            "foto", "fotos/oso-0042.jpg");

    @Test
    void salenSoloTextoNumeroFechaYLista() {
        assertThat(AtributosPublicos.filtrar(ficha, valores))
                .containsOnlyKeys("ancho_huella", "color", "fecha_muestra", "sexo");
    }

    @Test
    void laRutaGpsNuncaSalePorqueSonCoordenadas() {
        assertThat(AtributosPublicos.filtrar(ficha, valores)).doesNotContainKey("ruta_desplazamiento");
        assertThat(AtributosPublicos.filtrar(ficha, valores).toString()).doesNotContain("4.71");
    }

    @Test
    void unValorQueLaFichaNoDefineNoSale() {
        assertThat(AtributosPublicos.filtrar(ficha, Map.of("color", "pardo", "campo_raro", "x")))
                .containsOnlyKeys("color");
    }

    @Test
    void unAtributoRetiradoSigueSaliendoEnLosHallazgosViejos() {
        FichaTaxonomica sinColor = ficha.sinAtributo("color");

        assertThat(AtributosPublicos.filtrar(sinColor, Map.of("color", "pardo"))).containsEntry("color", "pardo");
    }

    @Test
    void losAtributosVacíosNoSalen() {
        assertThat(AtributosPublicos.filtrar(ficha, Map.of())).isEmpty();
    }
}
