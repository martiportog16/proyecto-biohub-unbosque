package co.edu.unbosque.biohub.persistencia.fichas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.GrupoTaxonomico;
import co.edu.unbosque.biohub.dominio.fichas.MembresiaGrupo;

@Repository
public class RepositorioGruposJdbc implements RepositorioGrupos {

    private static final TypeReference<List<DefinicionAtributo>> LISTA_ATRIBUTOS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RepositorioGruposJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<GrupoTaxonomico> grupos() {
        return jdbc.sql("SELECT id, nombre, descripcion FROM grupo_taxonomico ORDER BY nombre")
                .query(RepositorioGruposJdbc::grupo).list();
    }

    @Override
    public Optional<GrupoTaxonomico> grupo(long id) {
        return jdbc.sql("SELECT id, nombre, descripcion FROM grupo_taxonomico WHERE id = :id")
                .param("id", id).query(RepositorioGruposJdbc::grupo).optional();
    }

    @Override
    public boolean existeNombre(String nombre) {
        return jdbc.sql("SELECT count(*) FROM grupo_taxonomico WHERE lower(nombre) = lower(:n)")
                .param("n", nombre).query(Long.class).single() > 0;
    }

    @Override
    public long crear(String nombre, String descripcion) {
        return jdbc.sql("INSERT INTO grupo_taxonomico (nombre, descripcion) VALUES (:n, :d) RETURNING id")
                .param("n", nombre).param("d", descripcion).query(Long.class).single();
    }

    @Override
    public Optional<FichaTaxonomica> fichaVigenteDelGrupo(long grupoId) {
        return jdbc.sql("""
                SELECT grupo_id, version, atributos::text AS atributos FROM definicion_ficha_grupo
                 WHERE grupo_id = :grupo ORDER BY version DESC LIMIT 1""")
                .param("grupo", grupoId)
                .query((rs, fila) -> new FichaTaxonomica(rs.getLong("grupo_id"), rs.getInt("version"),
                        atributos(rs.getString("atributos"))))
                .optional();
    }

    @Override
    public void guardarVersionDelGrupo(FichaTaxonomica ficha, long autorId) {
        jdbc.sql("""
                INSERT INTO definicion_ficha_grupo (grupo_id, version, atributos, creada_por)
                VALUES (:grupo, :version, CAST(:atributos AS jsonb), :autor)""")
                .param("grupo", ficha.especieId())
                .param("version", ficha.version())
                .param("atributos", aJson(ficha.atributos()))
                .param("autor", autorId)
                .update();
    }

    @Override
    public void guardarVersionHeredada(FichaTaxonomica ficha, long grupoId, int versionDelGrupo, long autorId) {
        jdbc.sql("""
                INSERT INTO definicion_ficha (especie_id, version, atributos, creada_por,
                                              grupo_origen_id, version_grupo_origen)
                VALUES (:especie, :version, CAST(:atributos AS jsonb), :autor, :grupo, :versionGrupo)""")
                .param("especie", ficha.especieId())
                .param("version", ficha.version())
                .param("atributos", aJson(ficha.atributos()))
                .param("autor", autorId)
                .param("grupo", grupoId)
                .param("versionGrupo", versionDelGrupo)
                .update();
    }

    @Override
    public MembresiaGrupo membresia(long especieId) {
        return jdbc.sql("SELECT id, grupo_id, hereda_ficha FROM especie WHERE id = :id")
                .param("id", especieId).query(RepositorioGruposJdbc::membresia).single();
    }

    @Override
    public List<MembresiaGrupo> miembros(long grupoId) {
        return jdbc.sql("SELECT id, grupo_id, hereda_ficha FROM especie WHERE grupo_id = :grupo ORDER BY id")
                .param("grupo", grupoId).query(RepositorioGruposJdbc::membresia).list();
    }

    @Override
    public void cambiarMembresia(MembresiaGrupo m) {
        jdbc.sql("UPDATE especie SET grupo_id = :grupo, hereda_ficha = :hereda WHERE id = :id")
                .param("grupo", m.grupoId())
                .param("hereda", m.heredaFicha())
                .param("id", m.especieId())
                .update();
    }

    private static GrupoTaxonomico grupo(ResultSet rs, int fila) throws SQLException {
        return new GrupoTaxonomico(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"));
    }

    private static MembresiaGrupo membresia(ResultSet rs, int fila) throws SQLException {
        return new MembresiaGrupo(rs.getLong("id"), rs.getObject("grupo_id", Long.class), rs.getBoolean("hereda_ficha"));
    }

    private List<DefinicionAtributo> atributos(String texto) {
        try {
            return json.readValue(texto, LISTA_ATRIBUTOS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha de grupo con JSON inválido en la base", e);
        }
    }

    private String aJson(List<DefinicionAtributo> atributos) {
        try {
            return json.writeValueAsString(atributos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
