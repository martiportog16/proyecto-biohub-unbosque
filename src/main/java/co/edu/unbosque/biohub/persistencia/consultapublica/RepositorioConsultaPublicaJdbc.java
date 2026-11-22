package co.edu.unbosque.biohub.persistencia.consultapublica;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.consultapublica.PresenciaEnDepartamento;

@Repository
public class RepositorioConsultaPublicaJdbc implements RepositorioConsultaPublica {

    private final JdbcClient jdbc;

    public RepositorioConsultaPublicaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, Long> hallazgosPorEspecie() {
        Map<Long, Long> totales = new HashMap<>();
        jdbc.sql("SELECT especie_id, count(*) AS total FROM hallazgo GROUP BY especie_id")
                .query(rs -> {
                    totales.put(rs.getLong("especie_id"), rs.getLong("total"));
                });
        return totales;
    }

    @Override
    public List<PresenciaEnDepartamento> presencia(long especieId) {
        return jdbc.sql("""
                SELECT departamento, count(*) AS hallazgos, max(fecha_observacion) AS ultima
                  FROM hallazgo
                 WHERE especie_id = :especie
                 GROUP BY departamento
                 ORDER BY hallazgos DESC, departamento""")
                .param("especie", especieId)
                .query((rs, fila) -> new PresenciaEnDepartamento(
                        rs.getString("departamento"),
                        rs.getLong("hallazgos"),
                        rs.getObject("ultima", LocalDate.class)))
                .list();
    }
}
