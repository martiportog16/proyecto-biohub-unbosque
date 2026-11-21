package co.edu.unbosque.biohub.persistencia.coordenadas;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;

@Repository
public class EscrituraCoordenadasJdbc implements EscrituraCoordenadas {

    private final JdbcClient jdbc;

    public EscrituraCoordenadasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(long hallazgoId, Coordenada coordenada) {
        jdbc.sql("""
                INSERT INTO protegido.coordenada (hallazgo_id, latitud, longitud, precision_m)
                VALUES (:hallazgo, :lat, :lon, :precision)""")
                .param("hallazgo", hallazgoId)
                .param("lat", coordenada.latitud())
                .param("lon", coordenada.longitud())
                .param("precision", coordenada.precisionMetros())
                .update();
    }
}
