package co.edu.unbosque.biohub.persistencia.analitica;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.analitica.ConteoMensual;

/**
 * Usa la conexión PRINCIPAL del proceso. En el batch esa conexión es la réplica
 * de analítica (DB_URL del servicio "batch" en docker-compose.yml).
 */
@Repository
public class LecturaAnaliticaJdbc implements LecturaAnalitica {

    private final JdbcClient jdbc;

    public LecturaAnaliticaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConteoMensual> especiesPorDepartamentoYMes(YearMonth desde, YearMonth hasta) {
        // Cuenta con la especie ACTUAL de cada hallazgo (si se reclasificó, la nueva).
        return jdbc.sql("""
                SELECT departamento,
                       date_trunc('month', fecha_observacion)::date AS mes,
                       count(DISTINCT especie_id) AS especies
                  FROM hallazgo
                 WHERE fecha_observacion >= :desde AND fecha_observacion < :hasta
                 GROUP BY departamento, mes""")
                .param("desde", desde.atDay(1))
                .param("hasta", hasta.plusMonths(1).atDay(1))
                .query((rs, fila) -> new ConteoMensual(
                        rs.getString("departamento"),
                        YearMonth.from(rs.getObject("mes", LocalDate.class)),
                        rs.getInt("especies")))
                .list();
    }
}
