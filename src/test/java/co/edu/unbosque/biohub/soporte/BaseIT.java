package co.edu.unbosque.biohub.soporte;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import io.restassured.RestAssured;

/**
 * Base de las pruebas de integración (S21).
 *
 * Regla del curso: la prueba de integración usa el MISMO motor que producción
 * (PostgreSQL 16), nunca H2 ni un fake en memoria.
 *
 * - Si existe la variable BIOHUB_TEST_DB_URL (por ejemplo en el CI), se usan esa base
 *   y el Redis de localhost:6379.
 * - Si no, Testcontainers levanta un PostgreSQL 16 y un Redis 7 desechables.
 *   Así, en un computador del equipo, "mvnw verify" solo necesita Docker abierto.
 *
 * Arranca la app con el perfil "cientifico", que corre las migraciones de Flyway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cientifico")
public abstract class BaseIT {

    private static final String URL_EXTERNA = System.getenv("BIOHUB_TEST_DB_URL");
    private static final PostgreSQLContainer<?> POSTGRES;
    private static final GenericContainer<?> REDIS;

    static {
        if (URL_EXTERNA == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("biohub")
                    .withUsername("biohub")
                    .withPassword("biohub");
            REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            POSTGRES.start();
            REDIS.start();
        } else {
            POSTGRES = null;
            REDIS = null;
        }
    }

    @LocalServerPort
    protected int puerto;

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        String url = URL_EXTERNA != null ? URL_EXTERNA : POSTGRES.getJdbcUrl();
        String dueno = URL_EXTERNA != null ? envODefecto("BIOHUB_TEST_DB_USUARIO", "biohub") : POSTGRES.getUsername();
        String clave = URL_EXTERNA != null ? envODefecto("BIOHUB_TEST_DB_CLAVE", "biohub") : POSTGRES.getPassword();
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.flyway.url", () -> url);
        registro.add("spring.flyway.user", () -> dueno);
        registro.add("spring.flyway.password", () -> clave);
        if (REDIS != null) {
            registro.add("spring.data.redis.host", REDIS::getHost);
            registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        }
    }

    @BeforeEach
    void configurarRestAssured() {
        RestAssured.port = puerto;
    }

    /** Clave de todos los usuarios de demostración (V2__usuarios.sql). */
    protected static final String CLAVE_DEMO = "BioHub2026!";

    /** Inicia sesión con un usuario de demostración y devuelve su token JWT. */
    protected String tokenDe(String correo) {
        return RestAssured.given()
                .contentType("application/json")
                .body(java.util.Map.of("correo", correo, "clave", CLAVE_DEMO))
                .post("/api/cientifico/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private static String envODefecto(String nombre, String defecto) {
        String valor = System.getenv(nombre);
        return valor != null ? valor : defecto;
    }
}
