package co.edu.unbosque.biohub.presentacion.historial;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import co.edu.unbosque.biohub.soporte.BaseIT;
import co.edu.unbosque.biohub.soporte.RelojAjustable;

/**
 * HU-07 de punta a punta. Con un reloj ajustable se simula esta historia:
 *
 *   día 0   el investigador registra el hallazgo como especie 3 (Turdus fuscater)
 *   día 10  el director lo reclasifica a la especie 1
 *   día 20  el director lo reclasifica a la especie 2
 *   día 30  el auditor consulta (hoy)
 */
class HistorialIT extends BaseIT {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @TestConfiguration
    static class Reloj {
        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable(Instant.now(), BOGOTA);
        }
    }

    @Autowired
    RelojAjustable reloj;

    private int hallazgo;
    private LocalDate dia0;
    private String auditor;

    @BeforeEach
    void historia() {
        dia0 = LocalDate.now(reloj);
        auditor = tokenDe("auditor@biohub.co");
        String director = tokenDe("director@biohub.co");

        hallazgo = given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(Map.of("especieId", 3, "fechaObservacion", LocalDate.now(reloj).toString(),
                        "departamento", "Antioquia", "valores", Map.of("numero_individuos", 2),
                        "coordenada", Map.of("latitud", 6.25, "longitud", -75.56)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201).extract().path("id");

        reloj.adelantar(Duration.ofDays(10));
        reclasificar(director, 1, "Revisión del cráneo en laboratorio");
        reloj.adelantar(Duration.ofDays(10));
        reclasificar(director, 2, "Secuenciación de ADN confirma otra especie");
        reloj.adelantar(Duration.ofDays(10));
    }

    private void reclasificar(String director, int especie, String motivo) {
        given().auth().oauth2(director).contentType("application/json")
            .body(Map.of("especieNuevaId", especie, "motivo", motivo))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo)
            .then().statusCode(201);
    }

    @Test
    void elAuditorVeTodosLosCambiosEnOrdenConSusCincoDatos() {
        given().auth().oauth2(auditor)
            .get("/api/cientifico/hallazgos/{id}/historial", hallazgo)
        .then()
            .statusCode(200)
            .body("especieInicial.id", equalTo(3))
            .body("especieActual.id", equalTo(2))
            .body("cambios", hasSize(2))
            .body("cambios.especieAnterior.id", contains(3, 1))
            .body("cambios.especieNueva.id", contains(1, 2))
            .body("cambios.motivo", contains("Revisión del cráneo en laboratorio",
                    "Secuenciación de ADN confirma otra especie"))
            .body("cambios[0].autor.nombre", notNullValue())
            .body("cambios[0].realizadaEn", notNullValue());
    }

    @Test
    void compararUnaFechaEntreLosDosCambiosDevuelveLaEspecieIntermedia() {
        given().auth().oauth2(auditor)
            .queryParam("fecha", dia0.plusDays(15).toString())
            .get("/api/cientifico/hallazgos/{id}/historial/comparacion", hallazgo)
        .then()
            .statusCode(200)
            .body("existiaEnEsaFecha", equalTo(true))
            .body("especieEnEsaFecha.id", equalTo(1))
            .body("especieActual.id", equalTo(2))
            .body("cambioDesdeEntonces", equalTo(true))
            .body("cambiosDesdeEntonces.especieNueva.id", contains(2));
    }

    @Test
    void elMismoDiaDeUnCambioYaCuentaEseCambio() {
        given().auth().oauth2(auditor)
            .queryParam("fecha", dia0.plusDays(20).toString())
            .get("/api/cientifico/hallazgos/{id}/historial/comparacion", hallazgo)
        .then()
            .statusCode(200)
            .body("especieEnEsaFecha.id", equalTo(2))
            .body("cambioDesdeEntonces", equalTo(false))
            .body("cambiosDesdeEntonces", hasSize(0));
    }

    @Test
    void antesDeRegistrarseElHallazgoNoExistia() {
        // La fecha de registro la pone la base (now()), así que se toma de la respuesta
        String registradoEn = given().auth().oauth2(auditor)
                .get("/api/cientifico/hallazgos/{id}/historial", hallazgo).then().extract().path("registradoEn");
        LocalDate diaDeRegistro = Instant.parse(registradoEn).atZone(BOGOTA).toLocalDate();

        given().auth().oauth2(auditor)
            .queryParam("fecha", diaDeRegistro.minusDays(1).toString())
            .get("/api/cientifico/hallazgos/{id}/historial/comparacion", hallazgo)
        .then()
            .statusCode(200)
            .body("existiaEnEsaFecha", equalTo(false))
            .body("especieEnEsaFecha", nullValue())
            // Sin estado en esa fecha no hay "cambios desde entonces" (antes venían los 2 con la bandera en false)
            .body("cambioDesdeEntonces", equalTo(false))
            .body("cambiosDesdeEntonces", hasSize(0));
    }

    @Test
    void unaFechaFuturaSeRechaza() {
        given().auth().oauth2(auditor)
            .queryParam("fecha", LocalDate.now(reloj).plusDays(1).toString())
            .get("/api/cientifico/hallazgos/{id}/historial/comparacion", hallazgo)
        .then()
            .statusCode(422)
            .body("detalles", hasItem("fecha: no puede ser una fecha futura"));
    }

    @Test
    void elAuditorSoloPuedeLeer() {
        // HU-07, criterio 4: no puede reclasificar, ni editar ni borrar el historial
        given().auth().oauth2(auditor).contentType("application/json")
            .body(Map.of("especieNuevaId", 3, "motivo", "El auditor intenta cambiarlo"))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo)
            .then().statusCode(403);
        given().auth().oauth2(auditor)
            .delete("/api/cientifico/hallazgos/{id}/historial", hallazgo)
            .then().statusCode(405);

        given().auth().oauth2(auditor)
            .get("/api/cientifico/hallazgos/{id}/historial", hallazgo)
            .then().statusCode(200).body("cambios", hasSize(2));
    }

    @Test
    void otrosRolesNoVenElHistorial() {
        for (String correo : new String[] {"investigador@biohub.co", "analista@biohub.co", "altorango@biohub.co"}) {
            given().auth().oauth2(tokenDe(correo))
                .get("/api/cientifico/hallazgos/{id}/historial", hallazgo)
                .then().statusCode(403);
        }
        given().get("/api/cientifico/hallazgos/{id}/historial", hallazgo).then().statusCode(401);
    }
}
