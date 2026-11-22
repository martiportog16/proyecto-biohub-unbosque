package co.edu.unbosque.biohub.persistencia.integracion;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.integracion.Consumidor;

@Repository
public class RepositorioConsumidoresJdbc implements RepositorioConsumidores {

    private final JdbcClient jdbc;

    public RepositorioConsumidoresJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Consumidor> buscarPorHuella(String huellaApiKey) {
        return jdbc.sql("""
                SELECT id, nombre, limite_por_minuto, activo FROM consumidor WHERE api_key_hash = :huella""")
                .param("huella", huellaApiKey)
                .query((rs, fila) -> new Consumidor(rs.getLong("id"), rs.getString("nombre"),
                        rs.getInt("limite_por_minuto"), rs.getBoolean("activo")))
                .optional();
    }
}
