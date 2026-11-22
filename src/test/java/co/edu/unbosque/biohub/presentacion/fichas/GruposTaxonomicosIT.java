package co.edu.unbosque.biohub.presentacion.fichas;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * RF-02 y HU-02 (criterios 1 y 4): fichas por grupo taxonómico.
 * Cada prueba crea sus propios grupos y especies (FIRST, S20).
 */
class GruposTaxonomicosIT extends BaseIT {

    private String admin;

    @BeforeEach
    void sesion() {
        admin = tokenDe("admin.cientifico@biohub.co");
    }

    private int crearGrupo(String nombre) {
        return given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombre", nombre + " " + System.nanoTime(), "descripcion", "prueba",
                        "atributos", List.of(Map.of("nombre", "longitud_cm", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/grupos")
                .then().statusCode(201).body("versionFicha", equalTo(1))
                .extract().path("id");
    }

    private int crearEspecie() {
        return given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie grupo " + System.nanoTime(), "nombreComun", "Prueba",
                        "enPeligro", false,
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");
    }

    private void asignar(int grupo, int especie, boolean hereda) {
        given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("especieId", especie, "heredarFicha", hereda))
                .put("/api/cientifico/grupos/{id}/especies", grupo)
                .then().statusCode(200);
    }

    private List<String> atributosActivos(int especie) {
        return given().auth().oauth2(admin).get("/api/cientifico/especies/{id}/ficha", especie)
                .then().statusCode(200).extract().path("atributos.findAll { it.activo }.nombre");
    }

    private io.restassured.response.Response registrar(int especie, Map<String, Object> valores) {
        return given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", LocalDate.now().minusDays(1).toString(),
                        "departamento", "Meta", "valores", valores,
                        "coordenada", Map.of("latitud", 4.1, "longitud", -73.6, "precisionMetros", 10)))
                .post("/api/cientifico/hallazgos");
    }

    @Test
    void alHeredarLaEspecieRecibeLosAtributosDelGrupoYConservaLosSuyos() {
        int grupo = crearGrupo("Felinos");
        int especie = crearEspecie();

        asignar(grupo, especie, true);

        given().auth().oauth2(admin).get("/api/cientifico/especies/{id}/ficha", especie)
            .then().statusCode(200)
            .body("version", equalTo(2))
            .body("atributos.findAll { it.activo }.nombre", contains("longitud_cm"))
            // el atributo propio que el grupo no define queda retirado, no borrado
            .body("atributos.find { it.nombre == 'conteo' }.activo", equalTo(false));

        // El atributo del grupo ahora es obligatorio para registrar un hallazgo de la especie
        registrar(especie, Map.of()).then().statusCode(422);
        registrar(especie, Map.of("longitud_cm", 80)).then().statusCode(201);
    }

    @Test
    void elCambioDelGrupoLlegaSoloASusEspeciesNoALasDeOtroGrupo() {
        int aves = crearGrupo("Aves");
        int reptiles = crearGrupo("Reptiles");
        int ave1 = crearEspecie();
        int ave2 = crearEspecie();
        int reptil = crearEspecie();
        asignar(aves, ave1, true);
        asignar(aves, ave2, true);
        asignar(reptiles, reptil, true);

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "envergadura_cm", "tipo", "NUMERO", "obligatorio", false))
            .post("/api/cientifico/grupos/{id}/ficha/atributos", aves)
        .then()
            .statusCode(201)
            .body("ficha.version", equalTo(2))
            .body("especiesActualizadas.size()", equalTo(2));

        org.assertj.core.api.Assertions.assertThat(atributosActivos(ave1)).contains("envergadura_cm");
        org.assertj.core.api.Assertions.assertThat(atributosActivos(ave2)).contains("envergadura_cm");
        org.assertj.core.api.Assertions.assertThat(atributosActivos(reptil)).doesNotContain("envergadura_cm");
    }

    @Test
    void losHallazgosViejosConservanLaVersionConQueSeRegistraron() {
        int grupo = crearGrupo("Anfibios");
        int especie = crearEspecie();
        asignar(grupo, especie, true);
        int hallazgo = registrar(especie, Map.of("longitud_cm", 5)).then().statusCode(201).extract().path("id");

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "color_piel", "tipo", "TEXTO", "obligatorio", true))
            .post("/api/cientifico/grupos/{id}/ficha/atributos", grupo)
            .then().statusCode(201);

        given().auth().oauth2(tokenDe("investigador@biohub.co"))
            .get("/api/cientifico/hallazgos/{id}", hallazgo)
        .then()
            .statusCode(200)
            .body("versionFicha", equalTo(2));
    }

    @Test
    void unaEspecieQueSeEditoAManoYaNoRecibeCambiosDelGrupo() {
        int grupo = crearGrupo("Peces");
        int especie = crearEspecie();
        asignar(grupo, especie, true);

        // El administrador cambia la ficha de la especie directamente: se independiza
        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "aletas", "tipo", "NUMERO", "obligatorio", false))
            .post("/api/cientifico/especies/{id}/ficha/atributos", especie)
            .then().statusCode(201);

        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "escamas", "tipo", "TEXTO", "obligatorio", false))
            .post("/api/cientifico/grupos/{id}/ficha/atributos", grupo)
            .then().statusCode(201).body("especiesActualizadas.size()", equalTo(0));

        org.assertj.core.api.Assertions.assertThat(atributosActivos(especie))
                .contains("aletas").doesNotContain("escamas");
        given().auth().oauth2(admin).get("/api/cientifico/grupos/{id}", grupo)
            .then().body("especies.find { it.especieId == " + especie + " }.heredaFicha", equalTo(false));
    }

    @Test
    void unInvestigadorPuedeVerLosGruposPeroNoCambiarlos() {
        String investigador = tokenDe("investigador@biohub.co");

        given().auth().oauth2(investigador).get("/api/cientifico/grupos")
            .then().statusCode(200).body("nombre", hasItem("Primates"));
        given().auth().oauth2(investigador).contentType("application/json")
            .body(Map.of("nombre", "Insectos"))
            .post("/api/cientifico/grupos")
            .then().statusCode(403);
    }

    @Test
    void unGrupoConNombreRepetidoResponde422() {
        given().auth().oauth2(admin).contentType("application/json")
            .body(Map.of("nombre", "Primates"))
            .post("/api/cientifico/grupos")
        .then()
            .statusCode(422)
            .body("detalles", hasItem("nombre: ya existe 'Primates'"))
            .body("detalles", not(hasItem("nombre: es obligatorio")));
    }
}
