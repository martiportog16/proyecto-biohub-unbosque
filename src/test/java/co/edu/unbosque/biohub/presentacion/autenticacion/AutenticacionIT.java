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

    @Test
    void diezClavesEquivocadasBloqueanEseCorreoConUn429() {
        // Un correo inventado, para no bloquear a los usuarios que usan las demás pruebas
        String correo = "fuerza.bruta." + System.nanoTime() + "@biohub.co";
        for (int i = 0; i < 10; i++) {
            given().contentType("application/json").body(Map.of("correo", correo, "clave", "intento" + i))
                .post("/api/cientifico/auth/login").then().statusCode(401);
        }
        given().contentType("application/json").body(Map.of("correo", correo, "clave", "otra"))
            .post("/api/cientifico/auth/login")
        .then()
            .statusCode(429)
            .header("Retry-After", "900")
            .body("error", org.hamcrest.Matchers.containsString("Demasiados intentos fallidos"));
    }

    @Test
    void losErroresDeFormaSalenEnEspanolYConElMismoFormato() {
        given().contentType("application/json").body("{esto no es json")
            .post("/api/cientifico/auth/login")
            .then().statusCode(400).body("error", org.hamcrest.Matchers.startsWith("El cuerpo de la petición no es un JSON"));
        given().contentType("application/json").body(Map.of("correo", "", "clave", ""))
            .post("/api/cientifico/auth/login")
            .then().statusCode(400).body("detalles", org.hamcrest.Matchers.hasItems("correo: es obligatorio", "clave: es obligatoria"));
        // Con sesión, para que la seguridad no responda antes con 401
        given().auth().oauth2(tokenDe("investigador@biohub.co")).delete("/api/cientifico/hallazgos")
            .then().statusCode(405).body("error", equalTo("Esta dirección no acepta DELETE"));
        given().contentType("text/plain").body("hola")
            .post("/api/cientifico/auth/login")
            .then().statusCode(415).body("error", org.hamcrest.Matchers.startsWith("Envíe los datos como JSON"));
    }
}
