package co.edu.unbosque.biohub.persistencia.analitica;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.analitica.IndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;
import co.edu.unbosque.biohub.persistencia.comun.ConexionPrimaria;

/**
 * Usa ConexionPrimaria y no el JdbcClient normal: en el batch el JdbcClient normal
 * apunta a la réplica de analítica, que es de solo lectura.
 */
@Repository
public class RepositorioInformesJdbc implements RepositorioInformes {

    private final ConexionPrimaria primaria;
    private final JdbcClient jdbc;

    public RepositorioInformesJdbc(ConexionPrimaria primaria) {
        this.primaria = primaria;
        this.jdbc = primaria.jdbc();
    }

    @Override
    public boolean existe(YearMonth periodo) {
        return jdbc.sql("SELECT count(*) FROM informe_mensual WHERE periodo = :periodo")
                .param("periodo", periodo.atDay(1))
                .query(Long.class).single() > 0;
    }

    @Override
    public void guardar(InformeMensual informe) {
        primaria.enTransaccion(() -> {
            long id = jdbc.sql("""
                    INSERT INTO informe_mensual (periodo, generado_en, total_departamentos)
                    VALUES (:periodo, :generado, :total)
                    RETURNING id""")
                    .param("periodo", informe.periodo().atDay(1))
                    .param("generado", Timestamp.from(informe.generadoEn()))
                    .param("total", informe.indices().size())
                    .query(Long.class).single();
            for (IndicePresion indice : informe.indices()) {
                jdbc.sql("""
                        INSERT INTO indice_presion (informe_id, departamento, valor, especies_mes, base)
                        VALUES (:informe, :departamento, :valor, :especies, :base)""")
                        .param("informe", id)
                        .param("departamento", indice.departamento())
                        .param("valor", decimal(indice.valor()))
                        .param("especies", indice.especiesEnElMes())
                        .param("base", decimal(indice.base()))
                        .update();
            }
            return id;
        });
    }

    @Override
    public Optional<InformeMensual> buscar(YearMonth periodo) {
        return jdbc.sql("SELECT id, generado_en FROM informe_mensual WHERE periodo = :periodo")
                .param("periodo", periodo.atDay(1))
                .query((rs, fila) -> {
                    long id = rs.getLong("id");
                    Instant generado = rs.getTimestamp("generado_en").toInstant();
                    return new InformeMensual(periodo, generado, indicesDe(id));
                })
                .optional();
    }

    private List<IndicePresion> indicesDe(long informeId) {
        // Primero los de mayor presión; al final los que no tienen datos.
        return jdbc.sql("""
                SELECT departamento, valor, especies_mes, base
                  FROM indice_presion
                 WHERE informe_id = :informe
                 ORDER BY valor DESC NULLS LAST, departamento""")
                .param("informe", informeId)
                .query((rs, fila) -> new IndicePresion(
                        rs.getString("departamento"),
                        numero(rs.getBigDecimal("valor")),
                        rs.getInt("especies_mes"),
                        numero(rs.getBigDecimal("base"))))
                .list();
    }

    @Override
    public List<ResumenInforme> recientes(int limite) {
        return jdbc.sql("""
                SELECT periodo, generado_en, total_departamentos
                  FROM informe_mensual ORDER BY periodo DESC LIMIT :limite""")
                .param("limite", limite)
                .query((rs, fila) -> new ResumenInforme(
                        YearMonth.from(rs.getObject("periodo", LocalDate.class)),
                        rs.getTimestamp("generado_en").toInstant(),
                        rs.getInt("total_departamentos")))
                .list();
    }

    @Override
    public long crearSolicitud(YearMonth periodo, long usuarioId, Instant cuando) {
        return jdbc.sql("""
                INSERT INTO solicitud_informe (periodo, solicitado_por, solicitado_en)
                VALUES (:periodo, :usuario, :cuando)
                RETURNING id""")
                .param("periodo", periodo.atDay(1))
                .param("usuario", usuarioId)
                .param("cuando", Timestamp.from(cuando))
                .query(Long.class).single();
    }

    @Override
    public Optional<SolicitudInforme> solicitudPendiente(YearMonth periodo) {
        return jdbc.sql(SOLICITUDES + " WHERE s.atendida_en IS NULL AND s.periodo = :periodo ORDER BY s.id LIMIT 1")
                .param("periodo", periodo.atDay(1))
                .query(RepositorioInformesJdbc::solicitud).optional();
    }

    @Override
    public List<SolicitudInforme> solicitudesPendientes() {
        return jdbc.sql(SOLICITUDES + " WHERE s.atendida_en IS NULL ORDER BY s.id")
                .query(RepositorioInformesJdbc::solicitud).list();
    }

    @Override
    public List<SolicitudInforme> solicitudesRecientes(int limite) {
        return jdbc.sql(SOLICITUDES + " ORDER BY s.id DESC LIMIT :limite")
                .param("limite", limite)
                .query(RepositorioInformesJdbc::solicitud).list();
    }

    @Override
    public void marcarAtendida(long solicitudId, Instant cuando) {
        jdbc.sql("UPDATE solicitud_informe SET atendida_en = :cuando WHERE id = :id AND atendida_en IS NULL")
                .param("cuando", Timestamp.from(cuando))
                .param("id", solicitudId)
                .update();
    }

    private static final String SOLICITUDES = """
            SELECT s.id, s.periodo, s.solicitado_por, s.solicitado_en, s.atendida_en,
                   EXISTS (SELECT 1 FROM informe_mensual i WHERE i.periodo = s.periodo) AS con_informe
              FROM solicitud_informe s""";

    private static SolicitudInforme solicitud(ResultSet rs, int fila) throws SQLException {
        Timestamp atendida = rs.getTimestamp("atendida_en");
        return new SolicitudInforme(
                rs.getLong("id"),
                YearMonth.from(rs.getObject("periodo", LocalDate.class)),
                rs.getLong("solicitado_por"),
                rs.getTimestamp("solicitado_en").toInstant(),
                atendida == null ? null : atendida.toInstant(),
                rs.getBoolean("con_informe"));
    }

    private static BigDecimal decimal(Double valor) {
        return valor == null ? null : BigDecimal.valueOf(valor);
    }

    private static Double numero(BigDecimal valor) {
        return valor == null ? null : valor.doubleValue();
    }
}
