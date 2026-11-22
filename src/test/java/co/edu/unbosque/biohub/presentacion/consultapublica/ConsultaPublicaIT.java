package co.edu.unbosque.biohub.presentacion.consultapublica;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * HU-04 de punta a punta: un visitante SIN iniciar sesión consulta las fichas.
 * Usa una especie propia con 2 hallazgos en Meta y 1 en Huila.
 */
class ConsultaPublicaIT extends BaseIT {

    @Autowired
    StringRedisTemplate redis;

    private int especie;
    private String nombre;

    @BeforeEach
    void preparar() {
        redis.delete("publico:especies");
        String admin = tokenDe("admin.cientifico@biohub.co");
        nombre = "Especie publica " + System.nanoTime();
        especie = given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", nombre, "nombreComun", "Rana de prueba", "enPeligro", true,
                        "atributos", List.of(Map.of("nombre", "longitud_cuerpo", "tipo", "NUMERO",
                                "obligatorio", true, "unidad", "mm"))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");

        String investigador = tokenDe("investigador@biohub.co");
        for (String departamento : new String[] {"Meta", "Meta", "Huila"}) {
            given().auth().oauth2(investigador).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", LocalDate.now().minusDays(3).toString(),
                        "departamento", departamento, "valores", Map.of("longitud_cuerpo", 31),
                        "coordenada", Map.of("latitud", 3.98, "longitud", -73.76)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201);
        }
    }

    @Test
    void unVisitanteVeLaFichaSinIniciarSesion() {
        given()
            .get("/api/publico/especies/{id}", especie)
        .then()
            .statusCode(200)
            .body("nombreCientifico", equalTo(nombre))
            .body("atributos.nombre", contains("longitud_cuerpo"))
            .body("totalHallazgos", equalTo(3))
            .body("departamentos.departamento", contains("Meta", "Huila"))
            .body("departamentos.hallazgos", contains(2, 1));
    }

    @Test
    void ningunaRespuestaPublicaTraeCoordenadasNiDatosDelInvestigador() {
        // HU-04 criterio 4: 0 coordenadas exactas, aunque la especie esté en peligro
        String ficha = given().get("/api/publico/especies/{id}", especie).then().statusCode(200).extract().asString();
        String listado = given().get("/api/publico/especies").then().statusCode(200).extract().asString();

        for (String respuesta : List.of(ficha, listado)) {
            assertThat(respuesta).doesNotContainIgnoringCase("latitud")
                    .doesNotContainIgnoringCase("longitud\"")
                    .doesNotContainIgnoringCase("coordenada")
                    .doesNotContain("3.98")
                    .doesNotContainIgnoringCase("investigador");
        }
    }

    @Test
    void elVisitanteBuscaPorNombre() {
        given()
            .queryParam("buscar", nombre.toUpperCase())
            .get("/api/publico/especies")
        .then()
            .statusCode(200)
            .body("id", contains(especie))
            .body("[0].totalHallazgos", equalTo(3));

        given().queryParam("buscar", "oso").get("/api/publico/especies")
            .then().statusCode(200).body("nombreCientifico", hasItem("Tremarctos ornatus"));
    }

    @Test
    void laFichaQuedaEnRedisCompartidaPorTodasLasInstancias() {
        given().get("/api/publico/especies/{id}", especie).then().statusCode(200);

        assertThat(redis.hasKey("publico:ficha:" + especie)).isTrue();
        Long vida = redis.getExpire("publico:ficha:" + especie);
        assertThat(vida).isBetween(1L, 3L);
    }

    @Test
    void unaEspecieQueNoExisteResponde404() {
        given().get("/api/publico/especies/{id}", 999999).then().statusCode(404);
    }
}
