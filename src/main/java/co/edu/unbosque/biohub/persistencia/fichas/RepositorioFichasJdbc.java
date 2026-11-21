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
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;

@Repository
public class RepositorioFichasJdbc implements RepositorioFichas {

    private static final TypeReference<List<DefinicionAtributo>> LISTA_ATRIBUTOS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RepositorioFichasJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Especie> especies() {
        return jdbc.sql("SELECT id, nombre_cientifico, nombre_comun, en_peligro FROM especie ORDER BY nombre_cientifico")
                .query(this::mapearEspecie)
                .list();
    }

    @Override
    public Optional<Especie> especie(long id) {
        return jdbc.sql("SELECT id, nombre_cientifico, nombre_comun, en_peligro FROM especie WHERE id = :id")
                .param("id", id)
                .query(this::mapearEspecie)
                .optional();
    }

    @Override
    public long crearEspecie(String nombreCientifico, String nombreComun, boolean enPeligro) {
        return jdbc.sql("""
                INSERT INTO especie (nombre_cientifico, nombre_comun, en_peligro)
                VALUES (:cientifico, :comun, :peligro) RETURNING id""")
                .param("cientifico", nombreCientifico)
                .param("comun", nombreComun)
                .param("peligro", enPeligro)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean existeNombreCientifico(String nombreCientifico) {
        return jdbc.sql("SELECT count(*) FROM especie WHERE lower(nombre_cientifico) = lower(:n)")
                .param("n", nombreCientifico)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public Optional<FichaTaxonomica> fichaVigente(long especieId) {
        return jdbc.sql("""
                SELECT especie_id, version, atributos::text AS atributos FROM definicion_ficha
                WHERE especie_id = :especie ORDER BY version DESC LIMIT 1""")
                .param("especie", especieId)
                .query(this::mapearFicha)
                .optional();
    }

    @Override
    public Optional<FichaTaxonomica> fichaEnVersion(long especieId, int version) {
        return jdbc.sql("""
                SELECT especie_id, version, atributos::text AS atributos FROM definicion_ficha
                WHERE especie_id = :especie AND version = :version""")
                .param("especie", especieId)
                .param("version", version)
                .query(this::mapearFicha)
                .optional();
    }

    @Override
    public void guardarVersion(FichaTaxonomica ficha, long autorId) {
        jdbc.sql("""
                INSERT INTO definicion_ficha (especie_id, version, atributos, creada_por)
                VALUES (:especie, :version, CAST(:atributos AS jsonb), :autor)""")
                .param("especie", ficha.especieId())
                .param("version", ficha.version())
                .param("atributos", aJson(ficha.atributos()))
                .param("autor", autorId)
                .update();
    }

    private Especie mapearEspecie(ResultSet rs, int fila) throws SQLException {
        return new Especie(rs.getLong("id"), rs.getString("nombre_cientifico"),
                rs.getString("nombre_comun"), rs.getBoolean("en_peligro"));
    }

    private FichaTaxonomica mapearFicha(ResultSet rs, int fila) throws SQLException {
        try {
            List<DefinicionAtributo> atributos = json.readValue(rs.getString("atributos"), LISTA_ATRIBUTOS);
            return new FichaTaxonomica(rs.getLong("especie_id"), rs.getInt("version"), atributos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha con JSON inválido en la base", e);
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
