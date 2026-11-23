package co.edu.unbosque.biohub.dominio.hallazgos;

import java.util.ArrayList;
import java.util.List;

/**
 * Ubicación EXACTA de un hallazgo. Es información sensible: se guarda aparte
 * (esquema "protegido") y solo la lee el componente de autorización (RNF-06).
 *
 * Debe caer dentro del recuadro que contiene a Colombia, incluidos San Andrés y
 * Providencia y el Amazonas: BioHub es el inventario nacional. Antes se aceptaba
 * cualquier punto del planeta (la auditoría registró un hallazgo "en el Meta" con las
 * coordenadas de París). Es un recuadro, no la frontera exacta: rechaza los errores
 * grandes (signo cambiado, latitud y longitud invertidas), que son los comunes.
 */
public record Coordenada(double latitud, double longitud, Integer precisionMetros) {

    public static final double LATITUD_MINIMA = -4.3;
    public static final double LATITUD_MAXIMA = 13.6;
    public static final double LONGITUD_MINIMA = -82.0;
    public static final double LONGITUD_MAXIMA = -66.8;

    public List<String> errores() {
        List<String> errores = new ArrayList<>();
        if (latitud < LATITUD_MINIMA || latitud > LATITUD_MAXIMA) {
            errores.add("coordenada.latitud: debe estar dentro de Colombia (entre " + LATITUD_MINIMA + " y " + LATITUD_MAXIMA + ")");
        }
        if (longitud < LONGITUD_MINIMA || longitud > LONGITUD_MAXIMA) {
            errores.add("coordenada.longitud: debe estar dentro de Colombia (entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA
                    + "; en Colombia la longitud es negativa)");
        }
        if (precisionMetros != null && precisionMetros <= 0) {
            errores.add("coordenada.precisionMetros: debe ser mayor que 0");
        }
        return errores;
    }
}
