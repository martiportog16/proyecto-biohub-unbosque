package co.edu.unbosque.biohub.persistencia.usuarios;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.usuarios.CambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.TipoCambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

@Repository
public class RepositorioUsuariosJdbc implements RepositorioUsuarios, DirectorioPersonal, AdministracionUsuarios {

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

    /* ---------- Gestión de usuarios (P-28) ---------- */

    @Override
    public List<Usuario> todos() {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario ORDER BY activo DESC, nombre")
                .query(this::mapear)
                .list();
    }

    @Override
    public boolean existeCorreo(String correo) {
        return jdbc.sql("SELECT count(*) FROM usuario WHERE lower(correo) = lower(:correo)")
                .param("correo", correo)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public long crear(String correo, String nombre, String claveHash, Rol rol) {
        return jdbc.sql("INSERT INTO usuario (correo, nombre, clave_hash, rol) VALUES (:correo, :nombre, :clave, :rol) RETURNING id")
                .param("correo", correo)
                .param("nombre", nombre)
                .param("clave", claveHash)
                .param("rol", rol.name())
                .query(Long.class)
                .single();
    }

    @Override
    public void cambiarRol(long usuarioId, Rol rol) {
        jdbc.sql("UPDATE usuario SET rol = :rol WHERE id = :id").param("rol", rol.name()).param("id", usuarioId).update();
    }

    @Override
    public void cambiarActivo(long usuarioId, boolean activo) {
        jdbc.sql("UPDATE usuario SET activo = :activo WHERE id = :id").param("activo", activo).param("id", usuarioId).update();
    }

    @Override
    public void cambiarClave(long usuarioId, String claveHash) {
        jdbc.sql("UPDATE usuario SET clave_hash = :clave WHERE id = :id").param("clave", claveHash).param("id", usuarioId).update();
    }

    @Override
    public void registrar(CambioUsuario c) {
        jdbc.sql("""
                INSERT INTO cambio_usuario (usuario_id, autor_id, tipo, rol_anterior, rol_nuevo, motivo)
                VALUES (:usuario, :autor, :tipo, :anterior, :nuevo, :motivo)""")
                .param("usuario", c.usuarioId())
                .param("autor", c.autorId())
                .param("tipo", c.tipo().name())
                .param("anterior", c.rolAnterior() == null ? null : c.rolAnterior().name())
                .param("nuevo", c.rolNuevo() == null ? null : c.rolNuevo().name())
                .param("motivo", c.motivo())
                .update();
    }

    @Override
    public List<CambioUsuario> cambios(long usuarioId) {
        return jdbc.sql("""
                SELECT c.usuario_id, c.autor_id, a.nombre AS autor_nombre, c.tipo, c.rol_anterior, c.rol_nuevo,
                       c.motivo, c.realizado_en
                  FROM cambio_usuario c JOIN usuario a ON a.id = c.autor_id
                 WHERE c.usuario_id = :usuario
                 ORDER BY c.realizado_en, c.id""")
                .param("usuario", usuarioId)
                .query((rs, fila) -> new CambioUsuario(
                        rs.getLong("usuario_id"),
                        rs.getLong("autor_id"),
                        rs.getString("autor_nombre"),
                        TipoCambioUsuario.valueOf(rs.getString("tipo")),
                        rol(rs.getString("rol_anterior")),
                        rol(rs.getString("rol_nuevo")),
                        rs.getString("motivo"),
                        rs.getTimestamp("realizado_en").toInstant()))
                .list();
    }

    private static Rol rol(String valor) {
        return valor == null ? null : Rol.valueOf(valor);
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
