package co.edu.unbosque.biohub.presentacion.integracion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * PR-11 de punta a punta: filtro + PostgreSQL real + Redis real (localhost:6379 o el servicio del CI).
 *
 * El reloj se FIJA en un minuto al azar del futuro: así las 6 peticiones caen en la misma
 * ventana aunque la prueba se demore, y una ejecución no choca con la cuenta de la anterior.
 */
@Import(LimitePeticionesIT.RelojFijo.class)
class LimitePeticionesIT extends BaseIT {

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDePrueba() {
            long minutoAlAzar = ThreadLocalRandom.current().nextLong(30_000_000, 40_000_000);
            return Clock.fixed(Instant.ofEpochSecond(minutoAlAzar * 60 + 10), ZoneId.of("America/Bogota"));
        }
    }

    @Test
    void sinApiKeyResponde401() {
        given().get("/api/v1/estado").then().statusCode(401);
    }

    @Test
    void conApiKeyValidaRespondeElNombreDelConsumidor() {
        given().header("X-API-Key", "demo-unal-2026")
            .get("/api/v1/estado")
        .then()
            .statusCode(200)
            .body("consumidor", equalTo("Universidad Nacional (demo)"))
            .header("X-RateLimit-Limit", "60");
    }

    @Test
    void laOngConLimite5RecibeUn429EnLaSextaPeticionYLaUniversidadSigue() {
        for (int i = 1; i <= 5; i++) {
            given().header("X-API-Key", "demo-ong-2026").get("/api/v1/estado").then().statusCode(200);
        }

        given().header("X-API-Key", "demo-ong-2026")
            .get("/api/v1/estado")
        .then()
            .statusCode(429)
            .header("Retry-After", "50")
            .body("error", notNullValue());

        given().header("X-API-Key", "demo-unal-2026").get("/api/v1/estado").then().statusCode(200);
    }
}
