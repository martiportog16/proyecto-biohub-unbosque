package co.edu.unbosque.biohub.presentacion.integracion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * HU-10 criterio 4 / CU-12: el administrador cambia el límite de un consumidor
 * con el sistema corriendo, y aplica desde la siguiente petición.
 *
 * Reloj fijo en un minuto al azar, para que el contador de Redis empiece en 0
 * (igual que LimitePeticionesIT), y la cuenta se borra antes de cada prueba.
 */
class ConsumidoresIT extends BaseIT {

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDeLaPrueba() {
            long minutoAlAzar = ThreadLocalRandom.current().nextLong(30_000_000L, 40_000_000L);
            return Clock.fixed(Instant.ofEpochSecond(minutoAlAzar * 60 + 10), ZoneId.of("America/Bogota"));
        }
    }

    private static final String ONG = "demo-ong-2026";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    private String admin;
    private long idOng;

    @BeforeEach
    void preparar() {
        admin = tokenDe("admin.plataforma@biohub.co");
        idOng = jdbc.sql("SELECT id FROM consumidor WHERE nombre LIKE 'ONG%'").query(Long.class).single();
        // El reloj es el mismo minuto en todas las pruebas: se borra la cuenta para empezar en 0
        redis.delete(redis.keys("limite:" + idOng + ":*"));
    }

    @AfterEach
    void restaurar() {
        jdbc.sql("UPDATE consumidor SET limite_por_minuto = 5, activo = true WHERE id = :id").param("id", idOng).update();
    }

    private int pedir() {
        return given().header("X-API-Key", ONG).get("/api/v1/estado").statusCode();
    }

    private void cambiar(Map<String, Object> cambio, int estado) {
        given().auth().oauth2(admin).contentType("application/json").body(cambio)
            .patch("/api/cientifico/consumidores/{id}", idOng).then().statusCode(estado);
    }

    @Test
    void elAdministradorSubeElLimiteYAplicaEnLaSiguientePeticion() {
        for (int i = 0; i < 5; i++) {
            pedir();
        }
        org.assertj.core.api.Assertions.assertThat(pedir()).as("la sexta con límite 5").isEqualTo(429);

        cambiar(Map.of("limitePorMinuto", 8), 200);

        org.assertj.core.api.Assertions.assertThat(pedir()).as("la séptima con límite 8").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(pedir()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(pedir()).as("la novena con límite 8").isEqualTo(429);
    }

    @Test
    void desactivarUnConsumidorLeQuitaElAccesoDeInmediato() {
        org.assertj.core.api.Assertions.assertThat(pedir()).isEqualTo(200);

        cambiar(Map.of("activo", false), 200);

        org.assertj.core.api.Assertions.assertThat(pedir()).isEqualTo(401);
    }

    @Test
    void laListaMuestraLosLimitesPeroNuncaLaApiKey() {
        given().auth().oauth2(admin).get("/api/cientifico/consumidores")
        .then()
            .statusCode(200)
            .body("limitePorMinuto", hasItem(5))
            .body(not(containsString("api_key")))
            .body(not(containsString("f1d02d9b")));
    }

    @Test
    void unLimiteFueraDeRangoSeRechaza() {
        given().auth().oauth2(admin).contentType("application/json").body(Map.of("limitePorMinuto", 0))
            .patch("/api/cientifico/consumidores/{id}", idOng)
        .then()
            .statusCode(422)
            .body("detalles[0]", containsString("entre 1 y 10000"));
        given().auth().oauth2(admin).contentType("application/json").body(Map.of("limitePorMinuto", 50))
            .patch("/api/cientifico/consumidores/{id}", 999999).then().statusCode(404);
    }

    @Test
    void soloElAdministradorDeLaPlataformaCambiaLimites() {
        for (String correo : new String[] {"director@biohub.co", "admin.cientifico@biohub.co", "analista@biohub.co"}) {
            given().auth().oauth2(tokenDe(correo)).contentType("application/json")
                .body(Map.of("limitePorMinuto", 1000))
                .patch("/api/cientifico/consumidores/{id}", idOng).then().statusCode(403);
        }
        // Un consumidor externo con su API key tampoco
        given().header("X-API-Key", ONG).contentType("application/json").body(Map.of("limitePorMinuto", 1000))
            .patch("/api/cientifico/consumidores/{id}", idOng).then().statusCode(401);
        given().auth().oauth2(admin).get("/api/cientifico/consumidores")
            .then().body("find { it.id == " + idOng + " }.limitePorMinuto", equalTo(5));
    }
}
