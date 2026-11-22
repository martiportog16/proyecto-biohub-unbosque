package co.edu.unbosque.biohub.presentacion.coordenadas;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.soporte.BaseIT;
import io.restassured.response.Response;

/**
 * PR-07 (RNF-06, HU-08) de punta a punta: HTTP real + PostgreSQL real.
 *
 * La matriz: 8 perfiles × especie en peligro / fuera de peligro = 16 casos.
 * El resultado esperado: 0 accesos indebidos, y cada intento con su fila en el registro.
 *
 * Cada prueba crea sus propias especies y hallazgos. La coordenada usa números
 * raros a propósito (4.123456) para poder buscar si se "escapa" en alguna respuesta.
 */
class CoordenadasIT extends BaseIT {

    private static final String LATITUD = "4.123456";

    /** Los 7 perfiles que inician sesión y si son científicos. El octavo es el visitante (sin sesión). */
    private static final Map<String, Boolean> PERFILES = new LinkedHashMap<>();
    static {
        PERFILES.put("investigador@biohub.co", true);
        PERFILES.put("admin.cientifico@biohub.co", true);
        PERFILES.put("director@biohub.co", true);
        PERFILES.put("auditor@biohub.co", true);
        PERFILES.put("altorango@biohub.co", true);
        PERFILES.put("analista@biohub.co", false);
        PERFILES.put("admin.plataforma@biohub.co", false);
    }

    @Autowired
    JdbcClient jdbc;

    private int especieEnPeligro;
    private int especieComun;
    private int hallazgoEnPeligro;
    private int hallazgoComun;

    @BeforeEach
    void preparar() {
        especieEnPeligro = crearEspecie(true);
        especieComun = crearEspecie(false);
        hallazgoEnPeligro = crearHallazgo(especieEnPeligro);
        hallazgoComun = crearHallazgo(especieComun);
    }

    private int crearEspecie(boolean enPeligro) {
        return given().auth().oauth2(tokenDe("admin.cientifico@biohub.co")).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie reservada " + System.nanoTime(), "nombreComun", "Prueba",
                        "enPeligro", enPeligro,
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");
    }

    private int crearHallazgo(int especie) {
        return given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", LocalDate.now().minusDays(1).toString(),
                        "departamento", "Meta", "valores", Map.of("conteo", 4),
                        "coordenada", Map.of("latitud", 4.123456, "longitud", -73.654321, "precisionMetros", 15)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201).extract().path("id");
    }

    private Response pedirCoordenada(String correo, int hallazgo) {
        return given().auth().oauth2(tokenDe(correo)).get("/api/cientifico/hallazgos/{id}/coordenada", hallazgo);
    }

    private long filasDelRegistro(int hallazgo, boolean permitido) {
        return jdbc.sql("SELECT count(*) FROM protegido.acceso_coordenadas WHERE hallazgo_id = :h AND permitido = :p")
                .param("h", hallazgo).param("p", permitido).query(Long.class).single();
    }

    @Test
    void matrizDeRolesConCeroAccesosIndebidos() {
        int accesosIndebidos = 0;

        for (Map.Entry<String, Boolean> perfil : PERFILES.entrySet()) {
            String correo = perfil.getKey();
            boolean esAltoRango = correo.startsWith("altorango");
            boolean esCientifico = perfil.getValue();

            // Especie EN peligro: solo alto rango
            Response enPeligro = pedirCoordenada(correo, hallazgoEnPeligro);
            assertThat(enPeligro.statusCode()).as(correo + " · en peligro").isEqualTo(esAltoRango ? 200 : 403);
            if (!esAltoRango && enPeligro.asString().contains(LATITUD)) {
                accesosIndebidos++;
            }

            // Especie FUERA de peligro: cualquier rol científico
            Response comun = pedirCoordenada(correo, hallazgoComun);
            assertThat(comun.statusCode()).as(correo + " · fuera de peligro").isEqualTo(esCientifico ? 200 : 403);
            if (!esCientifico && comun.asString().contains(LATITUD)) {
                accesosIndebidos++;
            }
        }

        // Perfil 8: el visitante (sin sesión) nunca llega ni al componente
        for (int hallazgo : new int[] {hallazgoEnPeligro, hallazgoComun}) {
            Response visitante = given().get("/api/cientifico/hallazgos/{id}/coordenada", hallazgo);
            assertThat(visitante.statusCode()).as("visitante").isEqualTo(401);
            assertThat(visitante.asString()).doesNotContain(LATITUD);
        }

        assertThat(accesosIndebidos).as("accesos indebidos").isZero();

        // Evidencia (P-18): cada uno de los 14 intentos con sesión dejó su fila
        assertThat(filasDelRegistro(hallazgoEnPeligro, true)).as("en peligro, permitidos").isEqualTo(1);
        assertThat(filasDelRegistro(hallazgoEnPeligro, false)).as("en peligro, negados").isEqualTo(6);
        assertThat(filasDelRegistro(hallazgoComun, true)).as("fuera de peligro, permitidos").isEqualTo(5);
        assertThat(filasDelRegistro(hallazgoComun, false)).as("fuera de peligro, negados").isEqualTo(2);
    }

    @Test
    void altoRangoRecibeLaCoordenadaExacta() {
        pedirCoordenada("altorango@biohub.co", hallazgoEnPeligro)
        .then()
            .statusCode(200)
            .body("latitud", equalTo(4.123456f))
            .body("longitud", equalTo(-73.654321f))
            .body("precisionMetros", equalTo(15))
            .body("departamento", equalTo("Meta"));
    }

    @Test
    void elRechazoExplicaElMotivoYNoTraeCoordenadas() {
        pedirCoordenada("investigador@biohub.co", hallazgoEnPeligro)
        .then()
            .statusCode(403)
            .body("error", containsString("alto rango"))
            .body("error", containsString("departamento"));

        // y el hallazgo normal sigue mostrando solo el departamento
        String hallazgo = given().auth().oauth2(tokenDe("investigador@biohub.co"))
                .get("/api/cientifico/hallazgos/{id}", hallazgoEnPeligro).then().statusCode(200).extract().asString();
        assertThat(hallazgo).contains("Meta").doesNotContain(LATITUD);
    }

    @Test
    void unConsumidorExternoConApiKeyTampocoEntra() {
        // La API key sirve para /api/v1, no para el grupo científico
        given().header("X-API-Key", "demo-unal-2026")
            .get("/api/cientifico/hallazgos/{id}/coordenada", hallazgoComun)
            .then().statusCode(401);
    }

    @Test
    void elMapaDeLaEspecieSigueLaMismaRegla() {
        crearHallazgo(especieEnPeligro);

        given().auth().oauth2(tokenDe("altorango@biohub.co"))
            .get("/api/cientifico/especies/{id}/coordenadas", especieEnPeligro)
        .then()
            .statusCode(200)
            .body("enPeligro", equalTo(true))
            .body("total", equalTo(2))
            .body("puntos.latitud", everyItem(equalTo(4.123456f)));

        given().auth().oauth2(tokenDe("director@biohub.co"))
            .get("/api/cientifico/especies/{id}/coordenadas", especieEnPeligro)
            .then().statusCode(403);
        given().auth().oauth2(tokenDe("director@biohub.co"))
            .get("/api/cientifico/especies/{id}/coordenadas", especieComun)
            .then().statusCode(200).body("total", equalTo(1));

        // El mapa se registra con hallazgo_id vacío: 1 permitido y 1 negado para la especie en peligro
        List<Boolean> decisiones = jdbc.sql("""
                SELECT permitido FROM protegido.acceso_coordenadas
                 WHERE especie_id = :e AND hallazgo_id IS NULL ORDER BY id""")
                .param("e", especieEnPeligro).query(Boolean.class).list();
        assertThat(decisiones).containsExactly(true, false);
    }

    @Test
    void siAlUsuarioLoDesactivanSuTokenViejoYaNoSirveParaCoordenadas() {
        String tokenViejo = tokenDe("altorango@biohub.co");
        pedirConToken(tokenViejo).then().statusCode(200);
        try {
            jdbc.sql("UPDATE usuario SET activo = false WHERE correo = 'altorango@biohub.co'").update();

            pedirConToken(tokenViejo).then().statusCode(403);
        } finally {
            jdbc.sql("UPDATE usuario SET activo = true WHERE correo = 'altorango@biohub.co'").update();
        }
        pedirConToken(tokenViejo).then().statusCode(200);
    }

    private Response pedirConToken(String token) {
        return given().auth().oauth2(token).get("/api/cientifico/hallazgos/{id}/coordenada", hallazgoEnPeligro);
    }

    @Test
    void elAuditorConsultaElRegistroDeAccesosYSoloEl() {
        pedirCoordenada("altorango@biohub.co", hallazgoEnPeligro);
        pedirCoordenada("analista@biohub.co", hallazgoEnPeligro);

        String registro = given().auth().oauth2(tokenDe("auditor@biohub.co"))
            .queryParam("soloNegados", true).queryParam("limite", 5)
            .get("/api/cientifico/coordenadas/accesos")
        .then()
            .statusCode(200)
            .body("permitido", everyItem(equalTo(false)))
            .body("hallazgoId", hasItem(hallazgoEnPeligro))
            .body("[0].rol", equalTo("ANALISTA"))
            .body("[0].motivo", equalTo("ROL_NO_CIENTIFICO"))
            .extract().asString();
        assertThat(registro).as("el registro no contiene coordenadas").doesNotContain(LATITUD);

        for (String correo : new String[] {"altorango@biohub.co", "director@biohub.co", "admin.plataforma@biohub.co"}) {
            given().auth().oauth2(tokenDe(correo)).get("/api/cientifico/coordenadas/accesos").then().statusCode(403);
        }
    }

    @Test
    void elRegistroDeAccesosNoSePuedeEditarNiBorrarDesdeLaApp() {
        pedirCoordenada("investigador@biohub.co", hallazgoEnPeligro);

        for (String intento : new String[] {
                "UPDATE protegido.acceso_coordenadas SET permitido = true WHERE hallazgo_id = " + hallazgoEnPeligro,
                "DELETE FROM protegido.acceso_coordenadas WHERE hallazgo_id = " + hallazgoEnPeligro,
                "UPDATE protegido.coordenada SET latitud = 0 WHERE hallazgo_id = " + hallazgoEnPeligro}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql(intento).update())
                    .as(intento).rootCause().hasMessageContaining("permission denied");
        }
        assertThat(filasDelRegistro(hallazgoEnPeligro, false)).isEqualTo(1);
    }

    @Test
    void unHallazgoQueNoExisteResponde404() {
        pedirCoordenada("altorango@biohub.co", 99999999).then().statusCode(404);
        given().auth().oauth2(tokenDe("altorango@biohub.co"))
            .get("/api/cientifico/especies/{id}/coordenadas", 99999999).then().statusCode(404);
    }

    @Test
    void elListadoDeHallazgosNoTraeCoordenadasNiParaAltoRango() {
        // La única puerta a las coordenadas es el componente de autorización
        String listado = given().auth().oauth2(tokenDe("altorango@biohub.co"))
                .queryParam("especieId", especieEnPeligro).get("/api/cientifico/hallazgos")
                .then().statusCode(200).extract().asString();

        assertThat(listado).contains("Meta").doesNotContain(LATITUD).doesNotContainIgnoringCase("latitud");
    }
}
