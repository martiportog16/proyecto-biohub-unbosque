package co.edu.unbosque.biohub.presentacion.hallazgos;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * HU-01 de punta a punta: HTTP real + PostgreSQL real.
 * Especie 3 = Turdus fuscater (demostración): "numero_individuos" NUMERO obligatorio.
 */
class HallazgosIT extends BaseIT {

    @Autowired
    JdbcClient jdbc;

    private String investigador;

    @BeforeEach
    void sesion() {
        investigador = tokenDe("investigador@biohub.co");
    }

    private Map<String, Object> hallazgoValido(Map<String, Object> valores) {
        return Map.of(
                "especieId", 3,
                "fechaObservacion", LocalDate.now().minusDays(1).toString(),
                "departamento", "Cundinamarca",
                "valores", valores,
                "coordenada", Map.of("latitud", 4.6097, "longitud", -74.0817, "precisionMetros", 15));
    }

    private int registrar(Map<String, Object> cuerpo) {
        return given().auth().oauth2(investigador).contentType("application/json").body(cuerpo)
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201).extract().path("id");
    }

    @Test
    void unaPaginaFueraDeRangoResponde422YNoUnErrorDelServidor() {
        // La auditoría encontró que pagina=2147483647 desbordaba el OFFSET y daba 500
        for (String pagina : new String[] {"2147483647", "-1"}) {
            given().auth().oauth2(investigador).get("/api/cientifico/hallazgos?pagina=" + pagina)
                .then().statusCode(422)
                .body("detalles", org.hamcrest.Matchers.hasItem("pagina: debe estar entre 0 y 100000"));
        }
    }

    @Test
    void losDepartamentosSonLos33EnOrdenAlfabetico() {
        given().auth().oauth2(investigador).get("/api/cientifico/hallazgos/departamentos")
        .then()
            .statusCode(200)
            .body("size()", org.hamcrest.Matchers.equalTo(33))
            .body("[0]", org.hamcrest.Matchers.equalTo("Amazonas"))
            .body("[4]", org.hamcrest.Matchers.equalTo("Bogotá D.C."))
            .body("[32]", org.hamcrest.Matchers.equalTo("Vichada"));
    }

    @Test
    void elInvestigadorRegistraYLaCoordenadaQuedaEnElEsquemaProtegido() {
        int id = registrar(hallazgoValido(Map.of("numero_individuos", 4)));

        Long coordenadas = jdbc.sql("SELECT count(*) FROM protegido.coordenada WHERE hallazgo_id = :id")
                .param("id", id).query(Long.class).single();
        assertThat(coordenadas).isEqualTo(1);
    }

    @Test
    void laConsultaDelHallazgoNoIncluyeCoordenadas() {
        int id = registrar(hallazgoValido(Map.of("numero_individuos", 2)));

        given().auth().oauth2(investigador)
            .get("/api/cientifico/hallazgos/{id}", id)
        .then()
            .statusCode(200)
            .body("departamento", equalTo("Cundinamarca"))
            .body("$", not(hasKey("coordenada")))
            .body("$", not(hasKey("latitud")));
    }

    @Test
    void conDatosInvalidosResponde422ConTodosLosErroresYNoGuarda() {
        Long antes = jdbc.sql("SELECT count(*) FROM hallazgo").query(Long.class).single();

        given().auth().oauth2(investigador).contentType("application/json")
            .body(Map.of("especieId", 3, "fechaObservacion", "2999-01-01", "departamento", "Narnia",
                    "valores", Map.of("color", "azul"), "coordenada", Map.of("latitud", 4.6, "longitud", -74.0)))
            .post("/api/cientifico/hallazgos")
        .then()
            .statusCode(422)
            .body("detalles", hasItem("fechaObservacion: no puede ser una fecha futura"))
            .body("detalles", hasItem("numero_individuos: es obligatorio"))
            .body("detalles", hasItem("color: no es un atributo de esta ficha"));

        Long despues = jdbc.sql("SELECT count(*) FROM hallazgo").query(Long.class).single();
        assertThat(despues).isEqualTo(antes);
    }

    @Test
    void unDirectorNoPuedeRegistrarHallazgos() {
        given().auth().oauth2(tokenDe("director@biohub.co")).contentType("application/json")
            .body(hallazgoValido(Map.of("numero_individuos", 1)))
            .post("/api/cientifico/hallazgos")
        .then()
            .statusCode(403);
    }

    @Test
    void cambiarLaFichaDespuesNoAfectaLosHallazgosViejos() {
        // PR-01, parte "0 registros afectados". Usa una especie propia para no afectar otras pruebas.
        String admin = tokenDe("admin.cientifico@biohub.co");
        int especie = given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie hu01 " + System.nanoTime(), "nombreComun", "Prueba",
                        "atributos", List.of(Map.of("nombre", "numero_individuos", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");

        Map<String, Object> cuerpo = new java.util.HashMap<>(hallazgoValido(Map.of("numero_individuos", 7)));
        cuerpo.put("especieId", especie);
        int id = registrar(cuerpo);

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "habitat", "tipo", "TEXTO", "obligatorio", true))
            .post("/api/cientifico/especies/{id}/ficha/atributos", especie)
            .then().statusCode(201).body("version", equalTo(2));

        // el hallazgo viejo sigue igual, con la versión 1 de la ficha
        given().auth().oauth2(investigador)
            .get("/api/cientifico/hallazgos/{id}", id)
        .then()
            .statusCode(200)
            .body("valores.numero_individuos", equalTo(7))
            .body("versionFicha", equalTo(1));

        // y un hallazgo nuevo ya exige el atributo obligatorio nuevo
        cuerpo.put("valores", Map.of("numero_individuos", 1));
        given().auth().oauth2(investigador).contentType("application/json")
            .body(cuerpo)
            .post("/api/cientifico/hallazgos")
        .then()
            .statusCode(422)
            .body("detalles", equalTo(List.of("habitat: es obligatorio")));
    }
}
