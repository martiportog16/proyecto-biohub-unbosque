package co.edu.unbosque.biohub.presentacion.analitica;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.aplicacion.analitica.ProcesoInformes;
import co.edu.unbosque.biohub.soporte.BaseIT;
import co.edu.unbosque.biohub.soporte.PeriodosDePrueba;

/**
 * HU-11 de punta a punta con PostgreSQL real: hallazgos -> solicitud -> informe.
 *
 * Aquí "el batch" se simula llamando a ProcesoInformes a mano, porque las tareas
 * programadas solo existen en el perfil batch. BatchIT arranca el batch de verdad.
 *
 * Cada prueba usa un mes antiguo propio (PeriodosDePrueba), sin hallazgos de otras pruebas.
 */
class InformesIT extends BaseIT {

    @Autowired
    ProcesoInformes batch;

    @Autowired
    JdbcClient jdbc;

    private String investigador;

    private int crearEspecie() {
        return given().auth().oauth2(tokenDe("admin.cientifico@biohub.co")).contentType("application/json")
                .body(Map.of("nombreCientifico", "Especie informe " + System.nanoTime(), "nombreComun", "Prueba",
                        "atributos", List.of(Map.of("nombre", "conteo", "tipo", "NUMERO", "obligatorio", true))))
                .post("/api/cientifico/especies")
                .then().statusCode(201).extract().path("especieId");
    }

    /** Registra, por la API, un hallazgo de cada especie en ese departamento y mes. */
    private void observar(String departamento, YearMonth mes, int... especies) {
        for (int especie : especies) {
            given().auth().oauth2(investigador).contentType("application/json")
                .body(Map.of("especieId", especie, "fechaObservacion", mes.atDay(15).toString(),
                        "departamento", departamento, "valores", Map.of("conteo", 1),
                        "coordenada", Map.of("latitud", 2.5, "longitud", -75.5)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201);
        }
    }

    @Test
    void elAnalistaPideUnInformeElBatchLoGeneraYElAnalistaLoConsulta() {
        investigador = tokenDe("investigador@biohub.co");
        String analista = tokenDe("analista@biohub.co");
        int a = crearEspecie(), b = crearEspecie(), c = crearEspecie();
        YearMonth mes = PeriodosDePrueba.nuevo();

        // Meta: lo habitual son 2 especies (B = 2) y este mes solo se vio 1 -> 0,5
        observar("Meta", mes.minusMonths(3), a, b);
        observar("Meta", mes.minusMonths(2), a, b);
        observar("Meta", mes.minusMonths(1), a, b);
        observar("Meta", mes, a);
        observar("Meta", mes, a); // dos hallazgos de la MISMA especie cuentan una sola vez
        // Huila: se veía 1 especie cada mes (B = 1) y este mes ninguna -> 1,0
        observar("Huila", mes.minusMonths(3), a);
        observar("Huila", mes.minusMonths(2), a);
        observar("Huila", mes.minusMonths(1), a);
        // Cauca: B = (1 + 0 + 0) / 3 = 0,3333 y este mes se vieron 3 -> 0 (nunca negativo)
        observar("Cauca", mes.minusMonths(1), a);
        observar("Cauca", mes, a, b, c);
        // Nariño: primera vez que aparece (B = 0) -> sin datos
        observar("Nariño", mes, a);

        // 1. El analista lo pide: 202 y queda pendiente. La API no calcula nada.
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes")
        .then()
            .statusCode(202)
            .body("estado", equalTo("PENDIENTE"))
            .body("id", notNullValue());
        given().auth().oauth2(analista).get("/api/cientifico/informes/{mes}", mes.toString()).then().statusCode(404);

        // 2. El batch pasa y la atiende
        batch.atenderSolicitudes();

        // 3. El analista consulta los índices, de mayor a menor presión
        given().auth().oauth2(analista)
            .get("/api/cientifico/informes/{mes}", mes.toString())
        .then()
            .statusCode(200)
            .body("periodo", equalTo(mes.toString()))
            .body("totalDepartamentos", equalTo(4))
            .body("indices.departamento", contains("Huila", "Meta", "Cauca", "Nariño"))
            .body("indices.valor", contains(1.0f, 0.5f, 0.0f, null))
            .body("indices.especiesEnElMes", contains(0, 1, 3, 1))
            .body("indices.base", contains(1.0f, 2.0f, 0.3333f, 0.0f))
            .body("indices.sinDatos", contains(false, false, false, true));

        given().auth().oauth2(analista).get("/api/cientifico/informes")
            .then().statusCode(200).body("periodo", hasItem(mes.toString()));
        given().auth().oauth2(analista).get("/api/cientifico/informes/solicitudes")
            .then().statusCode(200).body("find { it.periodo == '" + mes + "' }.estado", equalTo("ATENDIDA"));

        // 4. Pedirlo otra vez no repite el trabajo
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes")
        .then()
            .statusCode(200)
            .body("estado", equalTo("INFORME_YA_EXISTE"))
            .body("id", nullValue());
    }

    @Test
    void unMesSinHallazgosNoGeneraInformeYSePuedeVolverAPedir() {
        String analista = tokenDe("analista@biohub.co");
        YearMonth mes = PeriodosDePrueba.nuevo();
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes").then().statusCode(202);

        batch.atenderSolicitudes();

        given().auth().oauth2(analista).get("/api/cientifico/informes/{mes}", mes.toString()).then().statusCode(404);
        given().auth().oauth2(analista).get("/api/cientifico/informes/solicitudes")
            .then().statusCode(200).body("find { it.periodo == '" + mes + "' }.estado", equalTo("SIN_DATOS"));
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes").then().statusCode(202).body("estado", equalTo("PENDIENTE"));
    }

    @Test
    void unInformeGeneradoNoSePuedeEditarNiBorrar() {
        investigador = tokenDe("investigador@biohub.co");
        String analista = tokenDe("analista@biohub.co");
        YearMonth mes = PeriodosDePrueba.nuevo();
        observar("Tolima", mes, crearEspecie());
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes").then().statusCode(202);
        batch.atenderSolicitudes();

        for (String intento : new String[] {
                "UPDATE informe_mensual SET total_departamentos = 99",
                "DELETE FROM indice_presion",
                "DELETE FROM informe_mensual"}) {
            assertThatThrownBy(() -> jdbc.sql(intento).update())
                    .as(intento).rootCause().hasMessageContaining("permission denied");
        }
        given().auth().oauth2(analista).delete("/api/cientifico/informes/{mes}", mes.toString())
            .then().statusCode(405);
    }

    @Test
    void soloSePidenMesesTerminadosYConFormatoValido() {
        String analista = tokenDe("analista@biohub.co");

        given().auth().oauth2(analista).contentType("application/json")
            .body(Map.of("periodo", YearMonth.now().toString()))
            .post("/api/cientifico/informes/solicitudes")
        .then()
            .statusCode(422)
            .body("detalles", hasItem("periodo: solo se generan informes de meses que ya terminaron"));

        given().auth().oauth2(analista).contentType("application/json").body("{}")
            .post("/api/cientifico/informes/solicitudes").then().statusCode(422);
        given().auth().oauth2(analista).get("/api/cientifico/informes/{mes}", "octubre").then().statusCode(400);
    }

    @Test
    void soloElAnalistaUsaLosInformes() {
        for (String correo : new String[] {"investigador@biohub.co", "director@biohub.co", "admin.plataforma@biohub.co"}) {
            given().auth().oauth2(tokenDe(correo)).get("/api/cientifico/informes").then().statusCode(403);
            given().auth().oauth2(tokenDe(correo)).contentType("application/json").body(Map.of("periodo", "2020-01"))
                .post("/api/cientifico/informes/solicitudes").then().statusCode(403);
        }
        given().get("/api/cientifico/informes").then().statusCode(401);
    }
}
