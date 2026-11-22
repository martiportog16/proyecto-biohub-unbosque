package co.edu.unbosque.biohub.presentacion.equipos;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * HU-03 (RF-03, RF-04) por la API real, con la base real.
 * Usuarios de demostración: 1 investigadora, 3 directora, 6 analista.
 */
class EquiposIT extends BaseIT {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private String investigador;

    @BeforeEach
    void sesion() {
        investigador = tokenDe("investigador@biohub.co");
    }

    private String codigoNuevo() {
        return "DRON-" + (System.nanoTime() % 1_000_000_000L);
    }

    private int registrar(String codigo, Object responsableId) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("codigo", codigo, "tipo", "DRON", "descripcion", "Mavic 3"));
        cuerpo.put("responsableId", responsableId);
        return given().auth().oauth2(investigador).contentType("application/json").body(cuerpo)
                .post("/api/cientifico/equipos")
                .then().statusCode(201).extract().path("id");
    }

    @Test
    void seRegistraUnEquipoYSeConsultaSuResponsableEnCualquierMomento() {   // criterios 1, 2 y 3
        String codigo = codigoNuevo();
        int id = registrar(codigo, 1);

        given().auth().oauth2(tokenDe("auditor@biohub.co")).get("/api/cientifico/equipos/{id}", id)
            .then().statusCode(200)
            .body("codigo", equalTo(codigo))
            .body("responsable.nombre", equalTo("Ana Investigadora"));
        given().auth().oauth2(investigador).get("/api/cientifico/equipos/codigo/{c}", codigo.toLowerCase())
            .then().statusCode(200).body("id", equalTo(id));
    }

    @Test
    void noSePuedeRegistrarUnEquipoSinResponsable() {   // criterio 4
        Map<String, Object> cuerpo = new HashMap<>(Map.of("codigo", codigoNuevo(), "tipo", "SENSOR"));
        cuerpo.put("responsableId", null);

        given().auth().oauth2(investigador).contentType("application/json").body(cuerpo)
            .post("/api/cientifico/equipos")
        .then()
            .statusCode(422)
            .body("detalles", hasItem("responsableId: un equipo no se puede registrar sin responsable"));
    }

    @Test
    void laBaseTampocoAceptaUnEquipoSinResponsable() {
        // Defensa en profundidad: aunque algo se saltara la aplicación, la columna es NOT NULL
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO equipo_campo (codigo, tipo, registrado_por) VALUES ('SIN-RESP', 'GPS', 1)").update())
            .rootCause().hasMessageContaining("responsable_id");
    }

    @Test
    void reasignarCambiaElResponsableYGuardaElHistorial() {
        int id = registrar(codigoNuevo(), 1);

        given().auth().oauth2(tokenDe("director@biohub.co")).contentType("application/json")
            .body(Map.of("responsableId", 3, "motivo", "Ana sale de vacaciones"))
            .put("/api/cientifico/equipos/{id}/responsable", id)
        .then()
            .statusCode(200)
            .body("responsable.nombre", equalTo("Marta Directora"));

        given().auth().oauth2(investigador).get("/api/cientifico/equipos/{id}/asignaciones", id)
            .then().statusCode(200)
            .body("motivo", contains("Registro del equipo", "Ana sale de vacaciones"))
            .body("responsable", contains("Ana Investigadora", "Marta Directora"));
        given().auth().oauth2(investigador).get("/api/cientifico/equipos?responsableId=1")
            .then().statusCode(200).body("id", not(hasItem(id)));
    }

    @Test
    void unAnalistaNoEsResponsablePosibleNiPuedeVerLosEquipos() {
        Map<String, Object> cuerpo = Map.of("codigo", codigoNuevo(), "tipo", "GPS", "responsableId", 6);
        given().auth().oauth2(investigador).contentType("application/json").body(cuerpo)
            .post("/api/cientifico/equipos")
            .then().statusCode(422).body("detalles", hasItem("responsableId: Pedro Analista no es personal científico"));

        given().auth().oauth2(investigador).get("/api/cientifico/equipos/responsables-posibles")
            .then().statusCode(200).body("nombre", not(hasItem("Pedro Analista")));
        given().auth().oauth2(tokenDe("analista@biohub.co")).get("/api/cientifico/equipos")
            .then().statusCode(403);
    }

    @Test
    void elAuditorConsultaPeroNoRegistra() {
        given().auth().oauth2(tokenDe("auditor@biohub.co")).contentType("application/json")
            .body(Map.of("codigo", codigoNuevo(), "tipo", "GPS", "responsableId", 1))
            .post("/api/cientifico/equipos")
            .then().statusCode(403);
    }
}
