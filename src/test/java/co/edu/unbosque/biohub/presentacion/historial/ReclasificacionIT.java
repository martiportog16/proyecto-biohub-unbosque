package co.edu.unbosque.biohub.presentacion.historial;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * PR-06 (RNF-05) y HU-06 de punta a punta: HTTP real + PostgreSQL real.
 *
 * Reclasificar guarda los 5 datos. Editar o borrar el historial falla:
 *   - por la API (no existen PUT ni DELETE),
 *   - por SQL con el usuario de la app (no tiene permiso),
 *   - por SQL con el DUEÑO de la base (el trigger lo rechaza).
 */
class ReclasificacionIT extends BaseIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Environment entorno;

    private String director;
    private int hallazgo;

    @BeforeEach
    void preparar() {
        director = tokenDe("director@biohub.co");
        // Un hallazgo propio de la prueba: especie 3 (Turdus fuscater)
        hallazgo = given().auth().oauth2(tokenDe("investigador@biohub.co")).contentType("application/json")
                .body(Map.of("especieId", 3, "fechaObservacion", LocalDate.now().minusDays(2).toString(),
                        "departamento", "Boyacá", "valores", Map.of("numero_individuos", 3),
                        "coordenada", Map.of("latitud", 5.53, "longitud", -73.36)))
                .post("/api/cientifico/hallazgos")
                .then().statusCode(201).extract().path("id");
    }

    private void reclasificar(String token, int especieNueva, String motivo, int estado) {
        given().auth().oauth2(token).contentType("application/json")
            .body(Map.of("especieNuevaId", especieNueva, "motivo", motivo))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo)
        .then().statusCode(estado);
    }

    private long filasDelHistorial() {
        return jdbc.sql("SELECT count(*) FROM reclasificacion WHERE hallazgo_id = :id")
                .param("id", hallazgo).query(Long.class).single();
    }

    @Test
    void elDirectorReclasificaYQuedanLosCincoDatos() {
        given().auth().oauth2(director).contentType("application/json")
            .body(Map.of("especieNuevaId", 1, "motivo", "Análisis de ADN mitocondrial"))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo)
        .then()
            .statusCode(201)
            .body("especieAnterior.id", equalTo(3))
            .body("especieNueva.nombreCientifico", equalTo("Ateles hybridus"))
            .body("autor.nombre", equalTo(given().auth().oauth2(director).get("/api/cientifico/usuarios/yo")
                    .then().extract().path("nombre")));

        Map<String, Object> fila = jdbc.sql("""
                SELECT r.especie_anterior_id, r.especie_nueva_id, r.motivo, u.correo, r.realizada_en
                  FROM reclasificacion r JOIN usuario u ON u.id = r.autor_id
                 WHERE r.hallazgo_id = :id""").param("id", hallazgo).query().singleRow();
        assertThat(fila.get("especie_anterior_id")).isEqualTo(3L);
        assertThat(fila.get("especie_nueva_id")).isEqualTo(1L);
        assertThat(fila.get("motivo")).isEqualTo("Análisis de ADN mitocondrial");
        assertThat(fila.get("correo")).isEqualTo("director@biohub.co");
        assertThat(fila.get("realizada_en")).isNotNull();

        // y el hallazgo ahora está en la especie nueva
        given().auth().oauth2(director).get("/api/cientifico/hallazgos/{id}", hallazgo)
            .then().statusCode(200).body("especieId", equalTo(1));
    }

    @Test
    void soloElDirectorPuedeReclasificar() {
        for (String correo : new String[] {"investigador@biohub.co", "auditor@biohub.co",
                "altorango@biohub.co", "admin.cientifico@biohub.co"}) {
            reclasificar(tokenDe(correo), 1, "Nueva evidencia genética", 403);
        }
        assertThat(filasDelHistorial()).isZero();
    }

    @Test
    void sinMotivoResponde422YNoGuardaNada() {
        given().auth().oauth2(director).contentType("application/json")
            .body(Map.of("especieNuevaId", 1, "motivo", " "))
            .post("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo)
        .then()
            .statusCode(422)
            .body("detalles", hasItem("motivo: es obligatorio"));

        assertThat(filasDelHistorial()).isZero();
    }

    @Test
    void laApiNoOfreceEditarNiBorrarElHistorial() {
        reclasificar(director, 1, "Nueva evidencia genética", 201);

        given().auth().oauth2(director).contentType("application/json").body(Map.of("motivo", "cambiado"))
            .put("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo).then().statusCode(405);
        given().auth().oauth2(director)
            .delete("/api/cientifico/hallazgos/{id}/reclasificaciones", hallazgo).then().statusCode(405);

        assertThat(filasDelHistorial()).isEqualTo(1);
    }

    @Test
    void elUsuarioDeLaAppNoTienePermisoParaEditarNiBorrar() {
        reclasificar(director, 1, "Nueva evidencia genética", 201);

        // jdbc usa app_cientifico, el mismo usuario de base de datos de la aplicación
        assertThatThrownBy(() -> jdbc.sql("UPDATE reclasificacion SET motivo = 'alterado a mano' WHERE hallazgo_id = :id")
                .param("id", hallazgo).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM reclasificacion WHERE hallazgo_id = :id")
                .param("id", hallazgo).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        // del hallazgo, la app solo puede cambiar la especie
        assertThatThrownBy(() -> jdbc.sql("UPDATE hallazgo SET departamento = 'Meta' WHERE id = :id")
                .param("id", hallazgo).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");

        assertThat(filasDelHistorial()).isEqualTo(1);
    }

    @Test
    void niSiquieraElDuenoDeLaBasePuedeEditarNiBorrar() throws SQLException {
        reclasificar(director, 1, "Nueva evidencia genética", 201);

        try (Connection dueno = DriverManager.getConnection(entorno.getProperty("spring.flyway.url"),
                entorno.getProperty("spring.flyway.user"), entorno.getProperty("spring.flyway.password"));
             Statement sql = dueno.createStatement()) {
            for (String intento : new String[] {
                    "UPDATE reclasificacion SET motivo = 'alterado por el dueño' WHERE hallazgo_id = " + hallazgo,
                    "DELETE FROM reclasificacion WHERE hallazgo_id = " + hallazgo,
                    "TRUNCATE reclasificacion"}) {
                assertThatThrownBy(() -> sql.execute(intento))
                        .as(intento)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("inmutable");
            }
        }
        assertThat(filasDelHistorial()).isEqualTo(1);
    }
}
