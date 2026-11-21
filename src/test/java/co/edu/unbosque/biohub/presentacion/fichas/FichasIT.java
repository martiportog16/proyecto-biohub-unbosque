package co.edu.unbosque.biohub.presentacion.fichas;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * PR-01 (RNF-01): 10 modificaciones de atributos por la API, con 0 cambios de código.
 * Cada prueba crea su propia especie para ser independiente (FIRST, S20).
 */
class FichasIT extends BaseIT {

    private String admin;

    @BeforeEach
    void sesion() {
        admin = tokenDe("admin.cientifico@biohub.co");
    }

    private int crearEspecie(String nombre) {
        return given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", nombre, "nombreComun", "Prueba", "enPeligro", false,
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).body("version", equalTo(1))
                .extract().path("especieId");
    }

    @Test
    void diezModificacionesDeAtributosSinCambiarCodigo() {
        int especie = crearEspecie("Especie pr01 " + System.nanoTime());

        for (int i = 1; i <= 5; i++) {
            given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombre", "dato_" + i, "tipo", "TEXTO", "obligatorio", false))
                .post("/api/cientifico/especies/{id}/ficha/atributos", especie)
                .then().statusCode(201);
            given().auth().oauth2(admin)
                .delete("/api/cientifico/especies/{id}/ficha/atributos/{nombre}", especie, "dato_" + i)
                .then().statusCode(200);
        }

        given().auth().oauth2(admin)
            .get("/api/cientifico/especies/{id}/ficha", especie)
        .then()
            .statusCode(200)
            .body("version", equalTo(11))
            .body("atributos", hasSize(6))                 // conteo + 5 retirados (no se borran)
            .body("atributos.findAll { it.activo }", hasSize(1));
    }

    @Test
    void unInvestigadorNoPuedeModificarFichas() {
        String investigador = tokenDe("investigador@biohub.co");

        given().auth().oauth2(investigador).contentType("application/json")
            .body(Map.of("nombre", "otro", "tipo", "TEXTO"))
            .post("/api/cientifico/especies/1/ficha/atributos")
        .then()
            .statusCode(403);
    }

    @Test
    void unAtributoMalDefinidoResponde422ConElMotivo() {
        int especie = crearEspecie("Especie invalida " + System.nanoTime());

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "sexo", "tipo", "LISTA", "opciones", List.of("macho")))
            .post("/api/cientifico/especies/{id}/ficha/atributos", especie)
        .then()
            .statusCode(422)
            .body("detalles", hasItem("opciones: un atributo de tipo LISTA necesita al menos 2 opciones"));
    }

    @Test
    void lasEspeciesDeDemostracionTienenSuFicha() {
        given().auth().oauth2(admin)
            .get("/api/cientifico/especies")
        .then()
            .statusCode(200)
            .body("nombreCientifico", hasItem("Tremarctos ornatus"));
    }
}
