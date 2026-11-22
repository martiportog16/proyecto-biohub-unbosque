package co.edu.unbosque.biohub.persistencia.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import co.edu.unbosque.biohub.soporte.BaseIT;

/**
 * PR-09 (RNF-06, T-14): la base de datos también protege, no solo el código.
 *
 * Cada rol de la aplicación se conecta con su PROPIO usuario de base de datos.
 * Aquí se conecta como cada uno, por SQL directo (como lo haría alguien que robó
 * la clave de ese usuario), y se comprueba qué puede y qué no.
 *
 * Lo central de PR-09: el usuario del grupo público (app_publico) NO puede leer el
 * esquema "protegido", donde están las coordenadas exactas. Aunque alguien tome el
 * control de una instancia pública, la base no le entrega coordenadas.
 */
class PermisosBaseDeDatosIT extends BaseIT {

    @Autowired
    Environment entorno;

    private String url;

    @BeforeEach
    void preparar() {
        url = entorno.getProperty("spring.datasource.url");
    }

    private Connection como(String usuario) throws SQLException {
        String clave = entorno.getProperty("spring.flyway.placeholders.clave_" + usuario);
        return DriverManager.getConnection(url, usuario, clave);
    }

    /** Ejecuta el SQL y devuelve el mensaje de error de PostgreSQL, o "OK" si funcionó. */
    private String intentar(String usuario, String sql) throws SQLException {
        try (Connection c = como(usuario); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                s.execute(sql);
                return "OK";
            } catch (SQLException e) {
                return e.getMessage();
            } finally {
                c.rollback(); // nada de lo que se intente aquí queda guardado
            }
        }
    }

    // ---------------- app_publico: el grupo público ----------------

    @Test
    void elUsuarioPublicoNoPuedeLeerLasCoordenadasExactas() throws SQLException {
        assertThat(intentar("app_publico", "SELECT latitud, longitud FROM protegido.coordenada LIMIT 1"))
                .contains("permission denied for schema protegido");
        assertThat(intentar("app_publico", "SELECT * FROM protegido.acceso_coordenadas LIMIT 1"))
                .contains("permission denied for schema protegido");
    }

    @Test
    void elUsuarioPublicoNiSiquieraPuedeVerQueTablasHayEnElEsquemaProtegido() throws SQLException {
        try (Connection c = como("app_publico"); Statement s = c.createStatement()) {
            var rs = s.executeQuery("SELECT has_schema_privilege('protegido', 'USAGE')");
            rs.next();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    void elUsuarioPublicoNoVeUsuariosNiClaves() throws SQLException {
        assertThat(intentar("app_publico", "SELECT correo, clave_hash FROM usuario LIMIT 1"))
                .contains("permission denied for table usuario");
    }

    @Test
    void elUsuarioPublicoSoloLee() throws SQLException {
        assertThat(intentar("app_publico", "SELECT count(*) FROM hallazgo")).isEqualTo("OK");
        assertThat(intentar("app_publico", "SELECT count(*) FROM especie")).isEqualTo("OK");
        for (String escritura : new String[] {
                "INSERT INTO especie (nombre_cientifico, nombre_comun) VALUES ('Intruso publico', 'x')",
                "UPDATE especie SET en_peligro = false",
                "DELETE FROM hallazgo",
                "UPDATE consumidor SET limite_por_minuto = 100000"}) {
            assertThat(intentar("app_publico", escritura)).as(escritura).contains("permission denied");
        }
    }

    // ---------------- app_batch: el proceso batch ----------------

    @Test
    void elUsuarioDelBatchTampocoVeCoordenadasNiUsuarios() throws SQLException {
        assertThat(intentar("app_batch", "SELECT * FROM protegido.coordenada LIMIT 1"))
                .contains("permission denied for schema protegido");
        assertThat(intentar("app_batch", "SELECT * FROM usuario LIMIT 1"))
                .contains("permission denied for table usuario");
    }

    // ---------------- app_cientifico: el grupo científico ----------------

    @Test
    void elUsuarioCientificoGuardaCoordenadasPeroNoLasCambiaNiLasBorra() throws SQLException {
        assertThat(intentar("app_cientifico", "SELECT count(*) FROM protegido.coordenada")).isEqualTo("OK");
        assertThat(intentar("app_cientifico", "UPDATE protegido.coordenada SET latitud = 0"))
                .contains("permission denied");
        assertThat(intentar("app_cientifico", "DELETE FROM protegido.coordenada"))
                .contains("permission denied");
        assertThat(intentar("app_cientifico", "DELETE FROM protegido.acceso_coordenadas"))
                .contains("permission denied");
    }

    @Test
    void elUsuarioCientificoNoPuedeBorrarHallazgosNiFichas() throws SQLException {
        assertThat(intentar("app_cientifico", "DELETE FROM hallazgo")).contains("permission denied");
        assertThat(intentar("app_cientifico", "DELETE FROM definicion_ficha")).contains("permission denied");
        assertThat(intentar("app_cientifico", "UPDATE definicion_ficha SET atributos = '[]'")).contains("permission denied");
    }

    // ---------------- Ninguno es administrador ----------------

    @Test
    void ningunUsuarioDeLaAplicacionEsSuperusuarioNiPuedeCrearRoles() throws SQLException {
        for (String usuario : new String[] {"app_publico", "app_cientifico", "app_batch"}) {
            try (Connection c = como(usuario); Statement s = c.createStatement()) {
                var rs = s.executeQuery("SELECT rolsuper, rolcreaterole, rolcreatedb, rolbypassrls "
                        + "FROM pg_roles WHERE rolname = current_user");
                rs.next();
                assertThat(rs.getBoolean(1)).as(usuario + " superusuario").isFalse();
                assertThat(rs.getBoolean(2)).as(usuario + " crea roles").isFalse();
                assertThat(rs.getBoolean(3)).as(usuario + " crea bases").isFalse();
                assertThat(rs.getBoolean(4)).as(usuario + " salta seguridad").isFalse();
            }
            assertThat(intentar(usuario, "CREATE TABLE intruso (x int)")).as(usuario).contains("permission denied");
        }
    }

    @Test
    void conUnaClaveEquivocadaNoSeEntra() {
        assertThatThrownBy(() -> DriverManager.getConnection(url, "app_publico", "clave-equivocada"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("authentication failed");
    }
}
