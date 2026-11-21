package co.edu.unbosque.biohub.presentacion;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * Prueba de humo: la app arranca contra PostgreSQL real, Flyway corre la V1
 * y el endpoint de estado responde con el rol de la instancia.
 */
class EstadoIT extends BaseIT {

    @Test
    void laInstanciaCientificaRespondeSuRol() {
        when()
            .get("/api/cientifico/estado")
        .then()
            .statusCode(200)
            .body("sistema", equalTo("BioHub"))
            .body("rol", equalTo("cientifico"));
    }
}
