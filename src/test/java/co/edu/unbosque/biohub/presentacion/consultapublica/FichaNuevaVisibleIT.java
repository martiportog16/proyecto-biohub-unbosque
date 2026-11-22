package co.edu.unbosque.biohub.presentacion.consultapublica;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * PR-16 (RNF-03, P-14): un cambio en una ficha se ve en el portal en ≤ 5 s.
 *
 * La vida de la caché se sube a 60 s en esta prueba: si el cambio aparece antes
 * de 5 s, es porque la escritura BORRÓ la caché, no porque la entrada venció sola.
 */
@TestPropertySource(properties = "biohub.cache.vida-segundos=60")
class FichaNuevaVisibleIT extends BaseIT {

    private static final Duration LIMITE = Duration.ofSeconds(5);

    /** Repite la consulta hasta que se cumple la condición y devuelve cuánto tardó. */
    private Duration esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long inicio = System.nanoTime();
        while (!condicion.getAsBoolean()) {
            if (Duration.ofNanos(System.nanoTime() - inicio).compareTo(LIMITE) > 0) {
                return Duration.ofNanos(System.nanoTime() - inicio);
            }
            Thread.sleep(100);
        }
        return Duration.ofNanos(System.nanoTime() - inicio);
    }

    @Test
    void unaEspecieNuevaApareceEnLaBusquedaPublicaEnMenosDe5Segundos() throws InterruptedException {
        String nombre = "Especie anunciada " + System.nanoTime();
        given().get("/api/publico/especies").then().statusCode(200); // el listado queda en caché

        given().auth().oauth2(tokenDe("admin.cientifico@biohub.co")).contentType("application/json")
            .body(Map.of("nombreCientifico", nombre, "nombreComun", "Nueva",
                    "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
            .post("/api/cientifico/especies").then().statusCode(201);

        Duration tardo = esperarHasta(() -> given().queryParam("buscar", nombre).get("/api/publico/especies")
                .then().extract().jsonPath().getList("nombreCientifico").contains(nombre));

        assertThat(tardo).isLessThanOrEqualTo(LIMITE);
    }

    @Test
    void unAtributoNuevoApareceEnLaFichaPublicaEnMenosDe5Segundos() throws InterruptedException {
        String admin = tokenDe("admin.cientifico@biohub.co");
        int especie = given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie cambiante " + System.nanoTime(), "nombreComun", "Cambia",
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies").then().statusCode(201).extract().path("especieId");
        given().get("/api/publico/especies/{id}", especie).then().statusCode(200); // la ficha queda en caché

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "color_plumaje", "tipo", "TEXTO", "obligatorio", false))
            .post("/api/cientifico/especies/{id}/ficha/atributos", especie).then().statusCode(201);

        Duration tardo = esperarHasta(() -> given().get("/api/publico/especies/{id}", especie)
                .then().extract().jsonPath().getList("atributos.nombre").contains("color_plumaje"));

        assertThat(tardo).isLessThanOrEqualTo(LIMITE);
    }
}
