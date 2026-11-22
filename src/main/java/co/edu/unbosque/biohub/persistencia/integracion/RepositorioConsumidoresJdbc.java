package co.edu.unbosque.biohub.persistencia.integracion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.integracion.Consumidor;

@Repository
public class RepositorioConsumidoresJdbc implements RepositorioConsumidores, AdministracionConsumidores {

    private static final String COLUMNAS = "SELECT id, nombre, limite_por_minuto, activo FROM consumidor";

    private final JdbcClient jdbc;

    public RepositorioConsumidoresJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Consumidor> buscarPorHuella(String huellaApiKey) {
        return jdbc.sql(COLUMNAS + " WHERE api_key_hash = :huella")
                .param("huella", huellaApiKey)
                .query(RepositorioConsumidoresJdbc::leer)
                .optional();
    }

    @Override
    public Optional<Consumidor> buscar(long id) {
        return jdbc.sql(COLUMNAS + " WHERE id = :id").param("id", id)
                .query(RepositorioConsumidoresJdbc::leer).optional();
    }

    @Override
    public List<Consumidor> listar() {
        return jdbc.sql(COLUMNAS + " ORDER BY nombre").query(RepositorioConsumidoresJdbc::leer).list();
    }

    @Override
    public void actualizar(Consumidor c) {
        jdbc.sql("UPDATE consumidor SET limite_por_minuto = :limite, activo = :activo WHERE id = :id")
                .param("limite", c.limitePorMinuto())
                .param("activo", c.activo())
                .param("id", c.id())
                .update();
    }

    private static Consumidor leer(ResultSet rs, int fila) throws SQLException {
        return new Consumidor(rs.getLong("id"), rs.getString("nombre"),
                rs.getInt("limite_por_minuto"), rs.getBoolean("activo"));
    }
}
