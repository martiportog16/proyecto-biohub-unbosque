package co.edu.unbosque.biohub.presentacion.analitica;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.BioHubApplication;
import co.edu.unbosque.biohub.aplicacion.analitica.ProcesoInformes;
import co.edu.unbosque.biohub.configuracion.ProgramacionBatchConfig;
import co.edu.unbosque.biohub.persistencia.comun.ConexionPrimaria;
import co.edu.unbosque.biohub.soporte.BaseIT;
import co.edu.unbosque.biohub.soporte.PeriodosDePrueba;

/**
 * El proceso batch DE VERDAD (HU-11 criterio 2, P-03, T-14).
 *
 * Esta prueba tiene dos aplicaciones corriendo, como en producción:
 *   - el grupo científico (lo arranca BaseIT), donde el analista usa la API;
 *   - el proceso batch, que se arranca aquí con el perfil "batch": otro contexto,
 *     sin servidor web, con el usuario de base de datos app_batch.
 *
 * En la prueba las dos conexiones del batch apuntan a la misma base (no hay réplica),
 * pero siguen siendo dos conexiones distintas: la principal es de SOLO LECTURA,
 * como la réplica. La réplica real se comprueba con docker compose (ver LEEME).
 */
class BatchIT extends BaseIT {

    private static ConfigurableApplicationContext batch;

    @Autowired
    Environment entorno;

    @Autowired
    ApplicationContext grupoCientifico;

    @BeforeEach
    void arrancarElBatchUnaVez() {
        if (batch != null) {
            return;
        }
        String url = entorno.getProperty("spring.datasource.url");
        String clave = entorno.getProperty("spring.flyway.placeholders.clave_app_batch");
        batch = new SpringApplicationBuilder(BioHubApplication.class)
                .profiles("batch")
                .run("--spring.datasource.url=" + url,
                        "--spring.datasource.username=app_batch",
                        "--spring.datasource.password=" + clave,
                        "--biohub.batch.primaria.url=" + url,
                        "--biohub.batch.primaria.usuario=app_batch",
                        "--biohub.batch.primaria.clave=" + clave,
                        // En la prueba el batch se llama a mano: las tareas programadas no deben adelantarse
                        "--biohub.batch.espera-inicial-ms=3600000");
    }

    @AfterAll
    static void apagarElBatch() {
        if (batch != null) {
            batch.close();
            batch = null;
        }
    }

    private JdbcClient conexionPrincipal() {
        return batch.getBean(JdbcClient.class);
    }

    private JdbcClient conexionPrimaria() {
        return batch.getBean(ConexionPrimaria.class).jdbc();
    }

    @Test
    void elBatchEsOtroProcesoSinServidorWebYSoloElTieneLasTareasProgramadas() {
        assertThat(batch).isNotInstanceOf(WebServerApplicationContext.class);
        assertThat(batch.getBeansOfType(ProgramacionBatchConfig.class)).hasSize(1);

        // El grupo científico NO puede generar informes por su cuenta
        assertThat(grupoCientifico.getBeansOfType(ProgramacionBatchConfig.class)).isEmpty();
    }

    @Test
    void laConexionPrincipalEsDeSoloLecturaYLaSegundaVaALaPrimaria() {
        assertThat(conexionPrincipal().sql("SHOW transaction_read_only").query(String.class).single()).isEqualTo("on");
        assertThat(conexionPrimaria().sql("SHOW transaction_read_only").query(String.class).single()).isEqualTo("off");
        assertThat(conexionPrincipal().sql("SELECT current_user").query(String.class).single()).isEqualTo("app_batch");
        assertThat(conexionPrimaria().sql("SELECT current_user").query(String.class).single()).isEqualTo("app_batch");

        // Por la conexión principal (la de la réplica) no se puede escribir nada
        assertThatThrownBy(() -> conexionPrincipal()
                .sql("INSERT INTO informe_mensual (periodo, total_departamentos) VALUES ('1800-01-01', 0)").update())
                .rootCause().hasMessageContaining("read-only");
    }

    @Test
    void elAnalistaPideElInformeEnElGrupoCientificoYElBatchLoGenera() {
        String analista = tokenDe("analista@biohub.co");
        YearMonth mes = PeriodosDePrueba.nuevo();
        // Un hallazgo de la especie 3 (Turdus fuscater) en ese mes, para que haya algo que calcular
        given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
            .body(Map.of("especieId", 3, "fechaObservacion", mes.atDay(10).toString(), "departamento", "Tolima",
                    "valores", Map.of("numero_individuos", 2), "coordenada", Map.of("latitud", 4.4, "longitud", -75.2)))
            .post("/api/cientifico/hallazgos").then().statusCode(201);
        given().auth().oauth2(analista).contentType("application/json").body(Map.of("periodo", mes.toString()))
            .post("/api/cientifico/informes/solicitudes").then().statusCode(202);

        int atendidas = batch.getBean(ProcesoInformes.class).atenderSolicitudes();

        assertThat(atendidas).isGreaterThanOrEqualTo(1);
        given().auth().oauth2(analista).get("/api/cientifico/informes/{mes}", mes.toString())
            .then().statusCode(200).body("periodo", equalTo(mes.toString())).body("totalDepartamentos", equalTo(1));
    }

    @Test
    void elUsuarioDelBatchTieneSoloLosPermisosQueNecesita() {
        // T-14, privilegios mínimos. Todo esto se intenta por la conexión a la PRIMARIA.
        String[] prohibido = {
                "UPDATE informe_mensual SET total_departamentos = 99",               // no edita informes
                "DELETE FROM indice_presion",                                         // ni los borra
                "DELETE FROM informe_mensual",
                "INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion,"
                        + " departamento, investigador_id) VALUES (1, 1, 1, '{}', '2020-01-01', 'Meta', 1)", // no registra
                "UPDATE hallazgo SET departamento = 'Meta'",
                "UPDATE solicitud_informe SET periodo = '2020-01-01'",                // solo puede marcar "atendida"
                "DELETE FROM solicitud_informe",
                "SELECT count(*) FROM usuario",                                       // no ve usuarios
                "SELECT count(*) FROM protegido.coordenada",                          // ni coordenadas
                "SELECT count(*) FROM protegido.acceso_coordenadas"};
        for (String intento : prohibido) {
            assertThatThrownBy(() -> conexionPrimaria().sql(intento).query().listOfRows())
                    .as(intento).rootCause().hasMessageContaining("permission denied");
        }

        // Lo único que sí necesita: leer hallazgos y solicitudes
        assertThat(conexionPrimaria().sql("SELECT count(*) FROM hallazgo").query(Long.class).single()).isNotNegative();
        assertThat(conexionPrimaria().sql("SELECT count(*) FROM solicitud_informe").query(Long.class).single()).isNotNegative();
    }
}
