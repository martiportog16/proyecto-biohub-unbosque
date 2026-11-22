package co.edu.unbosque.biohub.persistencia.historial;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.dominio.historial.Reclasificacion;

@Repository
public class RepositorioHistorialJdbc implements RepositorioHistorial {

    private final JdbcClient jdbc;

    public RepositorioHistorialJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<Long> registrar(Reclasificacion r) {
        // Control de concurrencia optimista: solo cambia si la especie sigue siendo la "anterior".
        // Si dos directores reclasifican a la vez, el segundo no pisa al primero.
        int cambiados = jdbc.sql("""
                UPDATE hallazgo SET especie_id = :nueva
                 WHERE id = :hallazgo AND especie_id = :anterior""")
                .param("nueva", r.especieNuevaId())
                .param("hallazgo", r.hallazgoId())
                .param("anterior", r.especieAnteriorId())
                .update();
        if (cambiados == 0) {
            return Optional.empty();
        }
        long id = jdbc.sql("""
                INSERT INTO reclasificacion (hallazgo_id, especie_anterior_id, especie_nueva_id,
                                             motivo, autor_id, realizada_en)
                VALUES (:hallazgo, :anterior, :nueva, :motivo, :autor, :fecha)
                RETURNING id""")
                .param("hallazgo", r.hallazgoId())
                .param("anterior", r.especieAnteriorId())
                .param("nueva", r.especieNuevaId())
                .param("motivo", r.motivo().strip())
                .param("autor", r.autorId())
                .param("fecha", Timestamp.from(r.realizadaEn()))
                .query(Long.class)
                .single();
        return Optional.of(id);
    }

    @Override
    public List<Reclasificacion> deHallazgo(long hallazgoId) {
        return jdbc.sql("""
                SELECT id, hallazgo_id, especie_anterior_id, especie_nueva_id, motivo, autor_id, realizada_en
                  FROM reclasificacion
                 WHERE hallazgo_id = :hallazgo
                 ORDER BY realizada_en, id""")
                .param("hallazgo", hallazgoId)
                .query((rs, fila) -> new Reclasificacion(
                        rs.getLong("id"),
                        rs.getLong("hallazgo_id"),
                        rs.getLong("especie_anterior_id"),
                        rs.getLong("especie_nueva_id"),
                        rs.getString("motivo"),
                        rs.getLong("autor_id"),
                        rs.getTimestamp("realizada_en").toInstant()))
                .list();
    }
}
