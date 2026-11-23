package co.edu.unbosque.biohub.dominio.fichas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DatosDeUbicacionTest {

    private final FichaTaxonomica ficha = new FichaTaxonomica(1, 1, List.of(
            new DefinicionAtributo("conteo", TipoDato.NUMERO, true, true, List.of(), null),
            new DefinicionAtributo("recorrido", TipoDato.RUTA_GPS, false, true, List.of(), null),
            new DefinicionAtributo("foto", TipoDato.ARCHIVO, false, true, List.of(), null),
            new DefinicionAtributo("viejo", TipoDato.RUTA_GPS, false, false, List.of(), null)));

    @Test
    void separaLaRutaYLosArchivosDeLoDemas() {
        var datos = DatosDeUbicacion.separar(ficha, Map.of("conteo", 3, "recorrido", List.of(Map.of("lat", 4, "lon", -74)), "foto", "a.jpg"));

        assertThat(datos.visibles()).containsOnlyKeys("conteo");
        assertThat(datos.ubicacion()).containsOnlyKeys("recorrido", "foto");
        assertThat(datos.nombresProtegidos()).containsExactlyInAnyOrder("recorrido", "foto");
    }

    @Test
    void unAtributoRetiradoConservaSuTipo() {
        // Se registró cuando "viejo" estaba activo; aunque hoy esté retirado sigue siendo de ubicación
        var datos = DatosDeUbicacion.separar(ficha, Map.of("viejo", List.of(Map.of("lat", 1, "lon", 1))));

        assertThat(datos.visibles()).isEmpty();
        assertThat(datos.ubicacion()).containsOnlyKeys("viejo");
    }

    @Test
    void loQueLaFichaNoDefineNoSaleNiComoVisibleNiComoUbicacion() {
        var datos = DatosDeUbicacion.separar(ficha, Map.of("intruso", "x"));

        assertThat(datos.visibles()).isEmpty();
        assertThat(datos.ubicacion()).isEmpty();
    }
}
