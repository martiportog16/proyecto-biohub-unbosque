package co.edu.unbosque.biohub.persistencia.equipos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.equipos.AsignacionEquipo;
import co.edu.unbosque.biohub.dominio.equipos.EquipoCampo;
import co.edu.unbosque.biohub.dominio.equipos.TipoEquipo;

@Repository
public class RepositorioEquiposJdbc implements RepositorioEquipos {

    private static final String COLUMNAS = "SELECT id, codigo, tipo, descripcion, responsable_id FROM equipo_campo ";
    private static final String MOTIVO_INICIAL = "Registro del equipo";

    private final JdbcClient jdbc;

    public RepositorioEquiposJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long registrar(EquipoCampo e, long registradoPor) {
        long id = jdbc.sql("""
                INSERT INTO equipo_campo (codigo, tipo, descripcion, responsable_id, registrado_por)
                VALUES (:codigo, :tipo, :descripcion, :responsable, :autor) RETURNING id""")
                .param("codigo", e.codigo())
                .param("tipo", e.tipo().name())
                .param("descripcion", e.descripcion())
                .param("responsable", e.responsableId())
                .param("autor", registradoPor)
                .query(Long.class).single();
        agregarAsignacion(id, e.responsableId(), registradoPor, MOTIVO_INICIAL);
        return id;
    }

    @Override
    public boolean existeCodigo(String codigo) {
        return jdbc.sql("SELECT count(*) FROM equipo_campo WHERE codigo = :c")
                .param("c", codigo).query(Long.class).single() > 0;
    }

    @Override
    public Optional<EquipoCampo> buscar(long id) {
        return jdbc.sql(COLUMNAS + "WHERE id = :id").param("id", id).query(RepositorioEquiposJdbc::equipo).optional();
    }

    @Override
    public Optional<EquipoCampo> buscarPorCodigo(String codigo) {
        return jdbc.sql(COLUMNAS + "WHERE codigo = :c").param("c", codigo).query(RepositorioEquiposJdbc::equipo).optional();
    }

    @Override
    public List<EquipoCampo> listar(Long responsableId) {
        return jdbc.sql(COLUMNAS + "WHERE (CAST(:r AS BIGINT) IS NULL OR responsable_id = :r) ORDER BY codigo")
                .param("r", responsableId).query(RepositorioEquiposJdbc::equipo).list();
    }

    @Override
    public boolean reasignar(long equipoId, long anterior, long nuevo, long asignadoPor, String motivo) {
        int filas = jdbc.sql("""
                UPDATE equipo_campo SET responsable_id = :nuevo
                 WHERE id = :id AND responsable_id = :anterior""")
                .param("nuevo", nuevo).param("id", equipoId).param("anterior", anterior)
                .update();
        if (filas == 0) {
            return false;
        }
        agregarAsignacion(equipoId, nuevo, asignadoPor, motivo);
        return true;
    }

    @Override
    public List<AsignacionEquipo> asignaciones(long equipoId) {
        return jdbc.sql("""
                SELECT a.equipo_id, a.responsable_id, u.nombre, a.asignado_por, a.asignado_en, a.motivo
                  FROM asignacion_equipo a JOIN usuario u ON u.id = a.responsable_id
                 WHERE a.equipo_id = :id ORDER BY a.asignado_en, a.id""")
                .param("id", equipoId)
                .query((rs, fila) -> new AsignacionEquipo(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getLong(4), rs.getTimestamp(5).toInstant(), rs.getString(6)))
                .list();
    }

    private void agregarAsignacion(long equipoId, long responsable, long asignadoPor, String motivo) {
        jdbc.sql("""
                INSERT INTO asignacion_equipo (equipo_id, responsable_id, asignado_por, motivo)
                VALUES (:equipo, :responsable, :autor, :motivo)""")
                .param("equipo", equipoId).param("responsable", responsable)
                .param("autor", asignadoPor).param("motivo", motivo)
                .update();
    }

    private static EquipoCampo equipo(ResultSet rs, int fila) throws SQLException {
        return new EquipoCampo(rs.getLong("id"), rs.getString("codigo"), TipoEquipo.valueOf(rs.getString("tipo")),
                rs.getString("descripcion"), rs.getLong("responsable_id"));
    }
}
