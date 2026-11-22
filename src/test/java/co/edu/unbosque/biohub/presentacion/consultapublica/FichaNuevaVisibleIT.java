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
 * PR-16 (RNF-03, P-14): un cambio en una ficha, un hallazgo nuevo o una reclasificación
 * se ven en el portal en ≤ 5 s (T-07).
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

    private Map<String, Object> hallazgo(int especie) {
        return Map.of("especieId", especie, "fechaObservacion", java.time.LocalDate.now().minusDays(1).toString(),
                "departamento", "Amazonas", "valores", Map.of("conteo", 1),
                "coordenada", Map.of("latitud", -1.2, "longitud", -70.1, "precisionMetros", 10));
    }

    private int especieNueva(String admin) {
        return given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie contada " + System.nanoTime(), "nombreComun", "Contada",
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies").then().statusCode(201).extract().path("especieId");
    }

    private long totalPublico(int especie) {
        return given().get("/api/publico/especies/{id}", especie).then().statusCode(200)
                .extract().jsonPath().getLong("totalHallazgos");
    }

    @Test
    void unHallazgoNuevoSeCuentaEnLaFichaPublicaEnMenosDe5Segundos() throws InterruptedException {   // T-07, secuencia 1
        int especie = especieNueva(tokenDe("admin.cientifico@biohub.co"));
        assertThat(totalPublico(especie)).isZero();   // la ficha queda en caché con 0 hallazgos

        given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json").body(hallazgo(especie))
            .post("/api/cientifico/hallazgos").then().statusCode(201);

        Duration tardo = esperarHasta(() -> totalPublico(especie) == 1);
        assertThat(tardo).isLessThanOrEqualTo(LIMITE);
    }

    @Test
    void alReclasificarElHallazgoPasaDeUnaFichaPublicaALaOtra() throws InterruptedException {   // T-07, secuencia 3
        String admin = tokenDe("admin.cientifico@biohub.co");
        int origen = especieNueva(admin);
        int destino = especieNueva(admin);
        int id = given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(hallazgo(origen)).post("/api/cientifico/hallazgos").then().statusCode(201).extract().path("id");
        esperarHasta(() -> totalPublico(origen) == 1);
        assertThat(totalPublico(destino)).isZero();   // las dos fichas quedan en caché

        given().auth().oauth2(tokenDe("director@biohub.co")).contentType("application/json")
            .body(Map.of("especieNuevaId", destino, "motivo", "Nueva evidencia genética"))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", id).then().statusCode(201);

        Duration tardo = esperarHasta(() -> totalPublico(origen) == 0 && totalPublico(destino) == 1);
        assertThat(tardo).isLessThanOrEqualTo(LIMITE);
    }
}
