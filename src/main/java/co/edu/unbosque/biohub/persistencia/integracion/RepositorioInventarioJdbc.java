package co.edu.unbosque.biohub.persistencia.integracion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RepositorioInventarioJdbc implements RepositorioInventario {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RepositorioInventarioJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<FilaInventario> pagina(Filtros f, long despuesDe, int limite) {
        // Sin investigador_id ni nada del esquema protegido: los externos no los reciben (P-30).
        return jdbc.sql("""
                SELECT h.id, h.especie_id, e.nombre_cientifico, e.nombre_comun, h.fecha_observacion,
                       h.departamento, h.ficha_especie_id, h.version_ficha, h.valores
                  FROM hallazgo h
                  JOIN especie e ON e.id = h.especie_id
                 WHERE h.id > :despuesDe
                   AND (CAST(:especie AS BIGINT) IS NULL OR h.especie_id = :especie)
                   AND (CAST(:departamento AS TEXT) IS NULL OR h.departamento = :departamento)
                   AND (CAST(:desde AS DATE) IS NULL OR h.fecha_observacion >= :desde)
                   AND (CAST(:hasta AS DATE) IS NULL OR h.fecha_observacion <= :hasta)
                 ORDER BY h.id
                 LIMIT :limite""")
                .param("despuesDe", despuesDe)
                .param("especie", f.especieId())
                .param("departamento", f.departamento())
                .param("desde", f.desde())
                .param("hasta", f.hasta())
                .param("limite", limite)
                .query(this::leer)
                .list();
    }

    private FilaInventario leer(ResultSet rs, int fila) throws SQLException {
        try {
            return new FilaInventario(rs.getLong("id"), rs.getLong("especie_id"), rs.getString("nombre_cientifico"),
                    rs.getString("nombre_comun"), rs.getObject("fecha_observacion", LocalDate.class),
                    rs.getString("departamento"), rs.getLong("ficha_especie_id"), rs.getInt("version_ficha"),
                    json.readValue(rs.getString("valores"), MAPA));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Hallazgo con JSON inválido en la base", e);
        }
    }
}
