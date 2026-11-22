package co.edu.unbosque.biohub.presentacion.integracion;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;
import io.restassured.path.json.JsonPath;

/**
 * HU-09 de punta a punta: una universidad consulta el inventario con su API key.
 *
 * Cada prueba crea una especie propia con 4 tipos de atributo, incluida una ruta GPS
 * y una foto, que NO deben salir hacia los externos. Las coordenadas usan números
 * raros (4.987654) para buscar si se "escapan" en alguna respuesta.
 */
class InventarioIT extends BaseIT {

    private static final String UNIVERSIDAD = "demo-unal-2026";

    private int especie;

    @BeforeEach
    void preparar() {
        especie = given().auth().oauth2(tokenDe("admin.cientifico@biohub.co")).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie inventario " + System.nanoTime(), "nombreComun", "Prueba",
                        "enPeligro", true,
                        "atributos", List.of(
                                Map.of("nombre", "longitud", "tipo", "NUMERO", "obligatorio", true, "unidad", "cm"),
                                Map.of("nombre", "color", "tipo", "TEXTO", "obligatorio", false),
                                Map.of("nombre", "recorrido", "tipo", "RUTA_GPS", "obligatorio", false),
                                Map.of("nombre", "foto", "tipo", "ARCHIVO", "obligatorio", false))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");
    }

    private void registrar(int cuantos, String departamento, LocalDate fecha) {
        String investigador = tokenDe("investigador@biohub.co");
        for (int i = 0; i < cuantos; i++) {
            given().auth().oauth2(investigador).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", fecha.toString(), "departamento", departamento,
                        "valores", Map.of("longitud", 30 + i, "color", "pardo",
                                "recorrido", List.of(Map.of("lat", 4.987654, "lon", -74.1), Map.of("lat", 4.99, "lon", -74.11)),
                                "foto", "fotos/especie-" + i + ".jpg"),
                        "coordenada", Map.of("latitud", 4.987654, "longitud", -74.123456)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201);
        }
    }

    private JsonPath consultar(Map<String, Object> parametros) {
        return given().header("X-API-Key", UNIVERSIDAD).queryParams(parametros)
                .get("/api/v1/hallazgos").then().statusCode(200).extract().jsonPath();
    }

    @Test
    void entregaEspecieFechaDepartamentoYSoloAtributosPublicos() {
        registrar(1, "Meta", LocalDate.now().minusDays(2));

        String cuerpo = given().header("X-API-Key", UNIVERSIDAD).queryParam("especieId", especie)
            .get("/api/v1/hallazgos")
        .then()
            .statusCode(200)
            .body("hallazgos", hasSize(1))
            .body("hallazgos[0].especie.id", equalTo(especie))
            .body("hallazgos[0].departamento", equalTo("Meta"))
            .body("hallazgos[0].fechaObservacion", equalTo(LocalDate.now().minusDays(2).toString()))
            .body("hallazgos[0].atributos.longitud", equalTo(30))
            .body("hallazgos[0].atributos.color", equalTo("pardo"))
            .body("tamanoPagina", equalTo(50))
            .body("siguiente", nullValue())
            .extract().asString();

        // Nunca: coordenadas, ruta GPS, archivos ni datos del investigador (P-30)
        assertThat(cuerpo).doesNotContain("4.987654").doesNotContain("-74.123456")
                .doesNotContain("recorrido").doesNotContain("foto")
                .doesNotContainIgnoringCase("latitud").doesNotContainIgnoringCase("coordenada")
                .doesNotContainIgnoringCase("investigador");
    }

    @Test
    void recorrePaginasDe50SinRepetirNiSaltarseNinguno() {
        registrar(53, "Huila", LocalDate.now().minusDays(1));

        List<Integer> vistos = new ArrayList<>();
        Integer cursor = null;
        int paginas = 0;
        do {
            Map<String, Object> parametros = new java.util.HashMap<>(Map.of("especieId", especie));
            if (cursor != null) {
                parametros.put("despuesDe", cursor);
            }
            JsonPath pagina = consultar(parametros);
            vistos.addAll(pagina.getList("hallazgos.id", Integer.class));
            cursor = pagina.get("siguiente");
            paginas++;
        } while (cursor != null);

        assertThat(paginas).isEqualTo(2);
        assertThat(vistos).hasSize(53).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void filtraPorDepartamentoYFechas() {
        registrar(2, "Meta", LocalDate.of(2026, 3, 10));
        registrar(1, "Cauca", LocalDate.of(2026, 3, 20));
        registrar(1, "Meta", LocalDate.of(2026, 6, 1));

        assertThat(consultar(Map.of("especieId", especie, "departamento", "Meta")).getList("hallazgos")).hasSize(3);
        assertThat(consultar(Map.of("especieId", especie, "desde", "2026-03-01", "hasta", "2026-03-31"))
                .getList("hallazgos.departamento")).containsExactlyInAnyOrder("Meta", "Meta", "Cauca");
        assertThat(consultar(Map.of("especieId", especie, "departamento", "Meta", "desde", "2026-05-01"))
                .getList("hallazgos")).hasSize(1);
        assertThat(consultar(Map.of("especieId", especie, "departamento", "Narnia")).getList("hallazgos")).isEmpty();
    }

    @Test
    void elCatalogoDeEspeciesTambienEstaDisponible() {
        given().header("X-API-Key", UNIVERSIDAD).get("/api/v1/especies")
        .then()
            .statusCode(200)
            .body("id", hasItem(especie))
            .body("nombreCientifico", hasItem("Tremarctos ornatus"));
    }

    @Test
    void sinApiKeyNoSeEntregaNada() {
        registrar(1, "Meta", LocalDate.now().minusDays(1));

        given().queryParam("especieId", especie).get("/api/v1/hallazgos").then().statusCode(401);
        given().header("X-API-Key", "llave-inventada").get("/api/v1/especies").then().statusCode(401);
        // Un token de la app científica no sirve como API key
        given().auth().oauth2(tokenDe("investigador@biohub.co")).get("/api/v1/hallazgos").then().statusCode(401);
    }

    @Test
    void parametrosInvalidosResponden422o400() {
        given().header("X-API-Key", UNIVERSIDAD).queryParam("despuesDe", -5)
            .get("/api/v1/hallazgos").then().statusCode(422);
        given().header("X-API-Key", UNIVERSIDAD).queryParam("desde", "2026-12-01").queryParam("hasta", "2026-01-01")
            .get("/api/v1/hallazgos").then().statusCode(422).body("detalles", contains("desde: no puede ser posterior a hasta"));
        given().header("X-API-Key", UNIVERSIDAD).queryParam("desde", "ayer")
            .get("/api/v1/hallazgos").then().statusCode(400);
    }

    @Test
    void cadaRespuestaDiceCuantasPeticionesLeQuedan() {
        given().header("X-API-Key", UNIVERSIDAD).get("/api/v1/especies")
        .then()
            .statusCode(200)
            .header("X-RateLimit-Limit", equalTo("60"))
            .body("$", everyItem(org.hamcrest.Matchers.notNullValue()));
    }
}
