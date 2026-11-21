package co.edu.unbosque.biohub.presentacion.autenticacion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * Prueba de integración del login (S21: petición HTTP real + PostgreSQL real).
 */
class AutenticacionIT extends BaseIT {

    @Test
    void unUsuarioDeDemostracionIniciaSesionYRecibeSuRol() {
        given()
            .contentType("application/json")
            .body(Map.of("correo", "investigador@biohub.co", "clave", CLAVE_DEMO))
        .when()
            .post("/api/cientifico/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("rol", equalTo("INVESTIGADOR"))
            .body("expiraEnSegundos", equalTo(3600));
    }

    @Test
    void conClaveIncorrectaResponde401() {
        given()
            .contentType("application/json")
            .body(Map.of("correo", "investigador@biohub.co", "clave", "mala"))
        .when()
            .post("/api/cientifico/auth/login")
        .then()
            .statusCode(401)
            .body("error", equalTo("Correo o clave incorrectos"));
    }

    @Test
    void sinTokenLasRutasCientificasResponden401() {
        given().get("/api/cientifico/usuarios/yo").then().statusCode(401);
    }

    @Test
    void conTokenLaRutaDevuelveQuienSoy() {
        String token = tokenDe("director@biohub.co");

        given()
            .auth().oauth2(token)
        .when()
            .get("/api/cientifico/usuarios/yo")
        .then()
            .statusCode(200)
            .body("correo", equalTo("director@biohub.co"))
            .body("rol", equalTo("DIRECTOR_CIENTIFICO"));
    }

    @Test
    void lasRutasPublicasNoPidenLogin() {
        given().get("/api/publico/estado").then().statusCode(200);
    }
}
