package co.edu.unbosque.biohub.persistencia.hallazgos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;

@Repository
public class RepositorioHallazgosJdbc implements RepositorioHallazgos {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };
    private static final String COLUMNAS = """
            id, especie_id, ficha_especie_id, version_ficha, valores::text AS valores,
            fecha_observacion, departamento, investigador_id, registrado_en""";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RepositorioHallazgosJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public long guardar(Hallazgo h) {
        return jdbc.sql("""
                INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores,
                                      fecha_observacion, departamento, investigador_id)
                VALUES (:especie, :fichaEspecie, :version, CAST(:valores AS jsonb), :fecha, :departamento, :investigador)
                RETURNING id""")
                .param("especie", h.especieId())
                .param("fichaEspecie", h.fichaEspecieId())
                .param("version", h.versionFicha())
                .param("valores", aJson(h.valores()))
                .param("fecha", h.fechaObservacion())
                .param("departamento", h.departamento())
                .param("investigador", h.investigadorId())
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<Hallazgo> buscar(long id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM hallazgo WHERE id = :id")
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    @Override
    public List<Hallazgo> listar(Long especieId, String departamento, int limite, int desplazamiento) {
        return jdbc.sql("SELECT " + COLUMNAS + """
                 FROM hallazgo
                WHERE (CAST(:especie AS BIGINT) IS NULL OR especie_id = :especie)
                  AND (CAST(:departamento AS VARCHAR) IS NULL OR departamento = :departamento)
                ORDER BY registrado_en DESC, id DESC
                LIMIT :limite OFFSET :desplazamiento""")
                .param("especie", especieId)
                .param("departamento", departamento)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(this::mapear)
                .list();
    }

    private Hallazgo mapear(ResultSet rs, int fila) throws SQLException {
        try {
            return new Hallazgo(
                    rs.getLong("id"),
                    rs.getLong("especie_id"),
                    rs.getLong("ficha_especie_id"),
                    rs.getInt("version_ficha"),
                    json.readValue(rs.getString("valores"), MAPA),
                    rs.getObject("fecha_observacion", java.time.LocalDate.class),
                    rs.getString("departamento"),
                    rs.getLong("investigador_id"),
                    rs.getTimestamp("registrado_en").toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Hallazgo con JSON inválido en la base", e);
        }
    }

    private String aJson(Map<String, Object> valores) {
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
