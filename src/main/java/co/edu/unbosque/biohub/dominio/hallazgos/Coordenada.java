package co.edu.unbosque.biohub.dominio.hallazgos;

import java.util.ArrayList;
import java.util.List;

/**
 * Ubicación EXACTA de un hallazgo. Es información sensible: se guarda aparte
 * (esquema "protegido") y solo la lee el componente de autorización (RNF-06).
 */
public record Coordenada(double latitud, double longitud, Integer precisionMetros) {

    public List<String> errores() {
        List<String> errores = new ArrayList<>();
        if (latitud < -90 || latitud > 90) {
            errores.add("coordenada.latitud: debe estar entre -90 y 90");
        }
        if (longitud < -180 || longitud > 180) {
            errores.add("coordenada.longitud: debe estar entre -180 y 180");
        }
        if (precisionMetros != null && precisionMetros <= 0) {
            errores.add("coordenada.precisionMetros: debe ser mayor que 0");
        }
        return errores;
    }
}
