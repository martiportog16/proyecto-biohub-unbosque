package co.edu.unbosque.biohub.presentacion.coordenadas;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * RNF-06 por la "puerta de atrás": los valores de la ficha que revelan la ubicación.
 *
 * La auditoría independiente encontró que la ruta GPS de un hallazgo de una especie en
 * peligro salía en GET /hallazgos para cualquier rol, sin pasar por la autorización.
 * Estas pruebas lo cierran: la ruta y los archivos solo salen por la autorización.
 */
class DatosDeUbicacionIT extends BaseIT {

    private static final String LATITUD_RUTA = "4.987654";
    private static final String ARCHIVO = "fotos/huella-secreta.jpg";
    private static final List<String> TODOS = List.of("investigador@biohub.co", "admin.cientifico@biohub.co",
            "director@biohub.co", "auditor@biohub.co", "altorango@biohub.co", "analista@biohub.co",
            "admin.plataforma@biohub.co");

    private int especie;
    private int hallazgo;

    @BeforeEach
    void preparar() {
        especie = given().auth().oauth2(tokenDe("admin.cientifico@biohub.co")).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie con ruta " + System.nanoTime(), "nombreComun", "Prueba",
                        "enPeligro", true,
                        "atributos", List.of(
                                Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true),
                                Map.of("nombre", "recorrido", "tipo", "RUTA_GPS", "obligatorio", false),
                                Map.of("nombre", "foto", "tipo", "ARCHIVO", "obligatorio", false))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");
        hallazgo = given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", LocalDate.now().minusDays(1).toString(),
                        "departamento", "Meta",
                        "valores", Map.of("conteo", 2, "foto", ARCHIVO,
                                "recorrido", List.of(Map.of("lat", 4.987654, "lon", -73.1), Map.of("lat", 4.99, "lon", -73.11))),
                        "coordenada", Map.of("latitud", 4.5, "longitud", -73.5)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201).extract().path("id");
    }

    @Test
    void ningunRolRecibeLaRutaNiElArchivoAlConsultarHallazgos() {
        for (String correo : TODOS) {
            String token = tokenDe(correo);
            String detalle = given().auth().oauth2(token).get("/api/cientifico/hallazgos/{id}", hallazgo)
                    .then().statusCode(200)
                    .body("valores.conteo", equalTo(2))
                    .body("valoresProtegidos", containsInAnyOrder("recorrido", "foto"))
                    .extract().asString();
            String lista = given().auth().oauth2(token).get("/api/cientifico/hallazgos?especieId={e}", especie)
                    .then().statusCode(200).extract().asString();
            assertThat(detalle).as(correo).doesNotContain(LATITUD_RUTA).doesNotContain(ARCHIVO);
            assertThat(lista).as(correo).doesNotContain(LATITUD_RUTA).doesNotContain(ARCHIVO);
        }
    }

    @Test
    void elAltoRangoRecibeLaRutaYElArchivoPorLaAutorizacion() {
        given().auth().oauth2(tokenDe("altorango@biohub.co")).get("/api/cientifico/hallazgos/{id}/coordenada", hallazgo)
        .then()
            .statusCode(200)
            .body("datosDeUbicacion.foto", equalTo(ARCHIVO))
            .body("datosDeUbicacion.recorrido.lat", contains(4.987654f, 4.99f));
    }

    @Test
    void losDemasRolesNoLaRecibenNiPorLaAutorizacion() {
        for (String correo : List.of("investigador@biohub.co", "director@biohub.co", "analista@biohub.co")) {
            String cuerpo = given().auth().oauth2(tokenDe(correo)).get("/api/cientifico/hallazgos/{id}/coordenada", hallazgo)
                    .then().statusCode(403).extract().asString();
            assertThat(cuerpo).as(correo).doesNotContain(LATITUD_RUTA).doesNotContain(ARCHIVO);
        }
    }

    @Test
    void laApiExternaTampocoLosEntrega() {
        String cuerpo = given().header("X-API-Key", "demo-unal-2026").get("/api/v1/hallazgos?especieId={e}", especie)
                .then().statusCode(200).extract().asString();
        assertThat(cuerpo).doesNotContain(LATITUD_RUTA).doesNotContain(ARCHIVO);
    }
}
