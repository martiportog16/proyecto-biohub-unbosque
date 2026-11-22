package co.edu.unbosque.biohub.presentacion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * T-11 (RNF-04): lo que pregunta el healthcheck de Docker a cada instancia.
 * Desde afuera estas rutas no existen: Nginx responde 404 en /actuator (PR-10).
 */
class SaludIT extends BaseIT {

    @Test
    void laInstanciaEstaListaCuandoSuBaseDeDatosResponde() {
        given().get("/actuator/health/readiness")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components", hasKey("readinessState"));
    }

    @Test
    void redisNoDecideSiLaInstanciaEstaLista() {
        // Si Redis cae, el portal sigue leyendo de la réplica: no se saca la instancia del balanceo
        given().get("/actuator/health/readiness")
            .then().statusCode(200).body("components", not(hasKey("redis")));
    }

    @Test
    void laInstanciaEstaViva() {
        given().get("/actuator/health/liveness").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void laSaludNoMuestraDetallesDeConexion() {
        given().get("/actuator/health/readiness")
            .then().statusCode(200).body("components.db", not(hasKey("details")));
    }
}
