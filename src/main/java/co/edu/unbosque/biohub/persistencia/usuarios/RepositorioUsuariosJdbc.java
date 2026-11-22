package co.edu.unbosque.biohub.persistencia.usuarios;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

@Repository
public class RepositorioUsuariosJdbc implements RepositorioUsuarios, DirectorioPersonal {

    private static final String COLUMNAS = "id, correo, nombre, clave_hash, rol, activo";

    private final JdbcClient jdbc;

    public RepositorioUsuariosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE lower(correo) = lower(:correo)")
                .param("correo", correo)
                .query(this::mapear)
                .optional();
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE id = :id")
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    @Override
    public List<Usuario> activos() {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE activo ORDER BY nombre")
                .query(this::mapear)
                .list();
    }

    private Usuario mapear(ResultSet rs, int fila) throws SQLException {
        return new Usuario(
                rs.getLong("id"),
                rs.getString("correo"),
                rs.getString("nombre"),
                rs.getString("clave_hash"),
                Rol.valueOf(rs.getString("rol")),
                rs.getBoolean("activo"));
    }
}
