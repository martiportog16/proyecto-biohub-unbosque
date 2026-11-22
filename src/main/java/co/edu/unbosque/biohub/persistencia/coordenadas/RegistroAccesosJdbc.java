package co.edu.unbosque.biohub.persistencia.coordenadas;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.coordenadas.AccesoCoordenadas;

@Repository
public class RegistroAccesosJdbc implements RegistroAccesos {

    private final JdbcClient jdbc;

    public RegistroAccesosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(AccesoCoordenadas a) {
        jdbc.sql("""
                INSERT INTO protegido.acceso_coordenadas
                       (usuario_id, rol, especie_id, hallazgo_id, en_peligro, permitido, motivo, ocurrido_en)
                VALUES (:usuario, :rol, :especie, :hallazgo, :enPeligro, :permitido, :motivo, :fecha)""")
                .param("usuario", a.usuarioId())
                .param("rol", a.rol())
                .param("especie", a.especieId())
                .param("hallazgo", a.hallazgoId())
                .param("enPeligro", a.especieEnPeligro())
                .param("permitido", a.permitido())
                .param("motivo", a.motivo())
                .param("fecha", Timestamp.from(a.ocurridoEn()))
                .update();
    }

    @Override
    public List<AccesoCoordenadas> recientes(boolean soloNegados, int limite) {
        return jdbc.sql("""
                SELECT id, usuario_id, rol, especie_id, hallazgo_id, en_peligro, permitido, motivo, ocurrido_en
                  FROM protegido.acceso_coordenadas
                 WHERE (NOT :soloNegados OR NOT permitido)
                 ORDER BY ocurrido_en DESC, id DESC
                 LIMIT :limite""")
                .param("soloNegados", soloNegados)
                .param("limite", limite)
                .query((rs, fila) -> new AccesoCoordenadas(
                        rs.getLong("id"),
                        rs.getLong("usuario_id"),
                        rs.getString("rol"),
                        rs.getLong("especie_id"),
                        rs.getObject("hallazgo_id", Long.class),
                        rs.getBoolean("en_peligro"),
                        rs.getBoolean("permitido"),
                        rs.getString("motivo"),
                        rs.getTimestamp("ocurrido_en").toInstant()))
                .list();
    }
}
