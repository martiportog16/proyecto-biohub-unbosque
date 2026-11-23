package co.edu.unbosque.biohub.presentacion.usuarios;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.not;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * Gestión de usuarios (P-28, S-01) por la API real, con la base real.
 * Cada prueba crea sus propios usuarios: así no le cambia el rol a los de demostración.
 */
class UsuariosIT extends BaseIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Environment entorno;

    private String plataforma;
    private String directora;

    @BeforeEach
    void sesion() {
        plataforma = tokenDe("admin.plataforma@biohub.co");
        directora = tokenDe("director@biohub.co");
    }

    private static String correoNuevo() {
        return "prueba-" + System.nanoTime() + "@biohub.co";
    }

    /** Crea un usuario como administrador de la plataforma; devuelve {id, claveTemporal, correo}. */
    private Map<String, Object> crear(String rol) {
        String correo = correoNuevo();
        Map<String, Object> creado = given().auth().oauth2(plataforma).contentType("application/json")
                .body(Map.of("correo", correo, "nombre", "Persona de prueba", "rol", rol, "motivo", "Ingresa al Instituto"))
                .post("/api/cientifico/usuarios")
                .then().statusCode(201).extract().path("$");
        @SuppressWarnings("unchecked")
        Map<String, Object> usuario = (Map<String, Object>) creado.get("usuario");
        return Map.of("id", usuario.get("id"), "clave", creado.get("claveTemporal"), "correo", correo);
    }

    private static int login(Object correo, Object clave) {
        return given().contentType("application/json").body(Map.of("correo", correo, "clave", clave))
                .post("/api/cientifico/auth/login").statusCode();
    }

    private static String rolDelToken(Object correo, Object clave) {
        return given().contentType("application/json").body(Map.of("correo", correo, "clave", clave))
                .post("/api/cientifico/auth/login").then().statusCode(200).extract().path("rol");
    }

    @Test
    void laPlataformaCreaUnUsuarioYSuClaveTemporalSirveUnaSolaVezParaEntrar() {
        Map<String, Object> u = crear("INVESTIGADOR");

        assertThat((String) u.get("clave")).hasSize(14);
        assertThat(rolDelToken(u.get("correo"), u.get("clave"))).isEqualTo("INVESTIGADOR");
        // La clave nunca se guarda en claro: solo su hash BCrypt
        String hash = jdbc.sql("SELECT clave_hash FROM usuario WHERE id = :id").param("id", u.get("id")).query(String.class).single();
        assertThat(hash).startsWith("$2a$").doesNotContain((String) u.get("clave"));
        // Y la lista nunca trae la clave ni el hash
        given().auth().oauth2(plataforma).get("/api/cientifico/usuarios")
            .then().statusCode(200).body(not(containsString("$2a$")));
    }

    @Test
    void unCorreoRepetidoOUnRolQueNoExisteSeRechazanConElMotivo() {
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("correo", "INVESTIGADOR@biohub.co", "nombre", "Otra Ana", "rol", "BRUJO", "motivo", "x"))
            .post("/api/cientifico/usuarios")
        .then()
            .statusCode(422)
            .body("detalles", contains("rol: no existe el rol BRUJO", "motivo: es obligatorio (al menos 5 caracteres)"));
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("correo", "INVESTIGADOR@biohub.co", "nombre", "Otra Ana", "rol", "ANALISTA", "motivo", "Ingresa al Instituto"))
            .post("/api/cientifico/usuarios")
            .then().statusCode(422).body("detalles", hasItem("correo: ya existe un usuario con el correo investigador@biohub.co"));
    }

    @Test
    void laPlataformaNoPuedeDarAltoRango() {   // S-01
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("correo", correoNuevo(), "nombre", "Quiere alto rango", "rol", "ALTO_RANGO", "motivo", "Lo pidió"))
            .post("/api/cientifico/usuarios")
            .then().statusCode(403).body("error", containsString("director científico"));

        Object id = crear("INVESTIGADOR").get("id");
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("rol", "ALTO_RANGO", "motivo", "Lo pidió la dirección"))
            .put("/api/cientifico/usuarios/{id}/rol", id)
            .then().statusCode(403).body("error", containsString("director científico"));
    }

    @Test
    void elDirectorDaYQuitaElAltoRangoYElNuevoTokenYaLoTrae() {   // S-01
        Map<String, Object> u = crear("INVESTIGADOR");

        given().auth().oauth2(directora).contentType("application/json")
            .body(Map.of("rol", "ALTO_RANGO", "motivo", "Lidera la expedición al Chocó"))
            .put("/api/cientifico/usuarios/{id}/rol", u.get("id"))
            .then().statusCode(200).body("rol", equalTo("ALTO_RANGO"));
        assertThat(rolDelToken(u.get("correo"), u.get("clave"))).isEqualTo("ALTO_RANGO");

        given().auth().oauth2(directora).contentType("application/json")
            .body(Map.of("rol", "INVESTIGADOR", "motivo", "Terminó la expedición"))
            .put("/api/cientifico/usuarios/{id}/rol", u.get("id"))
            .then().statusCode(200).body("rol", equalTo("INVESTIGADOR"));

        // La bitácora guarda los tres pasos, con autor y motivo
        given().auth().oauth2(plataforma).get("/api/cientifico/usuarios/{id}/cambios", u.get("id"))
            .then().statusCode(200)
            .body("tipo", contains("ALTA", "CAMBIO_ROL", "CAMBIO_ROL"))
            .body("rolNuevo", contains("INVESTIGADOR", "ALTO_RANGO", "INVESTIGADOR"))
            .body("autor", contains("Sofía Plataforma", "Marta Directora", "Marta Directora"))
            .body("motivo[1]", equalTo("Lidera la expedición al Chocó"));
    }

    @Test
    void elDirectorSoloTocaElAltoRangoYSoloVeAlPersonalCientifico() {
        Object id = crear("INVESTIGADOR").get("id");
        given().auth().oauth2(directora).contentType("application/json")
            .body(Map.of("rol", "AUDITOR_CIENTIFICO", "motivo", "Pasa a auditoría"))
            .put("/api/cientifico/usuarios/{id}/rol", id)
            .then().statusCode(403);
        given().auth().oauth2(directora).get("/api/cientifico/usuarios")
            .then().statusCode(200)
            .body("rol", everyItem(isOneOf("INVESTIGADOR", "ADMINISTRADOR_CIENTIFICO", "DIRECTOR_CIENTIFICO",
                    "AUDITOR_CIENTIFICO", "ALTO_RANGO")));
        // Y no puede dar alto rango a quien no es personal científico
        Object analista = crear("ANALISTA").get("id");
        given().auth().oauth2(directora).contentType("application/json")
            .body(Map.of("rol", "ALTO_RANGO", "motivo", "Lo necesita para un informe"))
            .put("/api/cientifico/usuarios/{id}/rol", analista)
            .then().statusCode(422).body("detalles", contains("rol: solo el personal científico puede ser de alto rango"));
    }

    @Test
    void nadieSeCambiaSuPropioRolNiSeDesactivaASiMismo() {
        long yo = jdbc.sql("SELECT id FROM usuario WHERE correo = 'admin.plataforma@biohub.co'").query(Long.class).single();
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("rol", "ANALISTA", "motivo", "Me cambio yo mismo"))
            .put("/api/cientifico/usuarios/{id}/rol", yo)
            .then().statusCode(403).body("error", equalTo("Nadie puede cambiar su propio rol"));
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("activo", false, "motivo", "Me desactivo"))
            .put("/api/cientifico/usuarios/{id}/estado", yo)
            .then().statusCode(403).body("error", equalTo("Nadie puede desactivarse a sí mismo"));
    }

    @Test
    void unUsuarioDesactivadoNoPuedeEntrarYAlReactivarloSi() {
        Map<String, Object> u = crear("AUDITOR_CIENTIFICO");
        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("activo", false, "motivo", "Terminó su contrato"))
            .put("/api/cientifico/usuarios/{id}/estado", u.get("id"))
            .then().statusCode(200).body("activo", equalTo(false));
        assertThat(login(u.get("correo"), u.get("clave"))).isEqualTo(401);

        given().auth().oauth2(plataforma).contentType("application/json")
            .body(Map.of("activo", true, "motivo", "Renovó su contrato"))
            .put("/api/cientifico/usuarios/{id}/estado", u.get("id"))
            .then().statusCode(200).body("activo", equalTo(true));
        assertThat(login(u.get("correo"), u.get("clave"))).isEqualTo(200);
    }

    @Test
    void cadaUsuarioCambiaSuClaveConLaActual() {
        Map<String, Object> u = crear("ANALISTA");
        String token = given().contentType("application/json").body(Map.of("correo", u.get("correo"), "clave", u.get("clave")))
                .post("/api/cientifico/auth/login").then().statusCode(200).extract().path("token");

        given().auth().oauth2(token).contentType("application/json")
            .body(Map.of("claveActual", "equivocada", "claveNueva", "una-clave-larga-2026"))
            .put("/api/cientifico/usuarios/yo/clave")
            .then().statusCode(422).body("detalles", contains("claveActual: no coincide con su clave actual"));
        given().auth().oauth2(token).contentType("application/json")
            .body(Map.of("claveActual", u.get("clave"), "claveNueva", "una-clave-larga-2026"))
            .put("/api/cientifico/usuarios/yo/clave")
            .then().statusCode(204);

        assertThat(login(u.get("correo"), u.get("clave"))).isEqualTo(401);
        assertThat(login(u.get("correo"), "una-clave-larga-2026")).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador@biohub.co", "admin.cientifico@biohub.co", "auditor@biohub.co",
            "altorango@biohub.co", "analista@biohub.co"})
    void losDemasRolesNoGestionanUsuarios(String correo) {
        String token = tokenDe(correo);
        given().auth().oauth2(token).get("/api/cientifico/usuarios").then().statusCode(403);
        given().auth().oauth2(token).contentType("application/json")
            .body(Map.of("correo", correoNuevo(), "nombre", "Intruso", "rol", "INVESTIGADOR", "motivo", "Me invento un usuario"))
            .post("/api/cientifico/usuarios").then().statusCode(403);
        given().auth().oauth2(token).contentType("application/json")
            .body(Map.of("rol", "ALTO_RANGO", "motivo", "Me doy alto rango"))
            .put("/api/cientifico/usuarios/{id}/rol", 1).then().statusCode(403);
    }

    @Test
    void laAplicacionNoPuedeBorrarUsuariosNiTocarLaBitacora() {   // privilegios mínimos (T-14)
        Object id = crear("INVESTIGADOR").get("id");
        // jdbc usa app_cientifico, el mismo usuario de base de datos de la aplicación
        for (String intento : new String[] {
                "UPDATE cambio_usuario SET motivo = 'alterado a mano' WHERE usuario_id = " + id,
                "DELETE FROM cambio_usuario WHERE usuario_id = " + id,
                "DELETE FROM usuario WHERE id = " + id,                       // un usuario nunca se borra: se desactiva
                "UPDATE usuario SET correo = 'otro@biohub.co' WHERE id = " + id}) {
            assertThatThrownBy(() -> jdbc.sql(intento).update()).as(intento)
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        }
    }

    @Test
    void niSiquieraElDuenoDeLaBasePuedeCambiarLaBitacora() throws SQLException {   // como el historial (RNF-05)
        Object id = crear("INVESTIGADOR").get("id");
        try (Connection dueno = DriverManager.getConnection(entorno.getProperty("spring.flyway.url"),
                entorno.getProperty("spring.flyway.user"), entorno.getProperty("spring.flyway.password"));
             Statement sql = dueno.createStatement()) {
            for (String intento : new String[] {
                    "UPDATE cambio_usuario SET motivo = 'alterado por el dueño' WHERE usuario_id = " + id,
                    "DELETE FROM cambio_usuario WHERE usuario_id = " + id,
                    "TRUNCATE cambio_usuario"}) {
                assertThatThrownBy(() -> sql.execute(intento)).as(intento)
                    .isInstanceOf(SQLException.class).hasMessageContaining("inmutable");
            }
        }
    }
}
