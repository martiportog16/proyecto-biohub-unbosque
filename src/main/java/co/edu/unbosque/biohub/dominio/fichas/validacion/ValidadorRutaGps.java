package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * Una ruta GPS: al menos 2 puntos {"lat": ..., "lon": ...}.
 * Latitud entre -90 y 90; longitud entre -180 y 180 (los bordes son válidos).
 */
public class ValidadorRutaGps implements ValidadorDeTipo {

    @Override
    public TipoDato tipo() {
        return TipoDato.RUTA_GPS;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof List<?> puntos) || puntos.size() < 2) {
            return Optional.of("debe ser una lista de al menos 2 puntos {lat, lon}");
        }
        for (int i = 0; i < puntos.size(); i++) {
            if (!puntoValido(puntos.get(i))) {
                return Optional.of("el punto " + (i + 1) + " no tiene lat entre -90 y 90 y lon entre -180 y 180");
            }
        }
        return Optional.empty();
    }

    private boolean puntoValido(Object punto) {
        if (!(punto instanceof Map<?, ?> mapa)
                || !(mapa.get("lat") instanceof Number lat)
                || !(mapa.get("lon") instanceof Number lon)) {
            return false;
        }
        double la = lat.doubleValue();
        double lo = lon.doubleValue();
        return la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
    }
}
