package co.edu.unbosque.biohub.persistencia.coordenadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta;
import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;

@Repository
public class LecturaCoordenadasJdbc implements LecturaCoordenadas {

    private static final String CONSULTA = """
            SELECT h.id, h.departamento, h.fecha_observacion, c.latitud, c.longitud, c.precision_m
              FROM protegido.coordenada c
              JOIN hallazgo h ON h.id = c.hallazgo_id
            """;

    private final JdbcClient jdbc;

    public LecturaCoordenadasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UbicacionExacta> deHallazgo(long hallazgoId) {
        return jdbc.sql(CONSULTA + " WHERE h.id = :hallazgo")
                .param("hallazgo", hallazgoId)
                .query(LecturaCoordenadasJdbc::leer)
                .optional();
    }

    @Override
    public List<UbicacionExacta> deEspecie(long especieId, int limite) {
        return jdbc.sql(CONSULTA + """
                 WHERE h.especie_id = :especie
                 ORDER BY h.fecha_observacion DESC, h.id DESC
                 LIMIT :limite""")
                .param("especie", especieId)
                .param("limite", limite)
                .query(LecturaCoordenadasJdbc::leer)
                .list();
    }

    private static UbicacionExacta leer(ResultSet rs, int fila) throws SQLException {
        return new UbicacionExacta(
                rs.getLong("id"),
                rs.getString("departamento"),
                rs.getObject("fecha_observacion", LocalDate.class),
                new Coordenada(rs.getDouble("latitud"), rs.getDouble("longitud"),
                        rs.getObject("precision_m", Integer.class)));
    }
}
