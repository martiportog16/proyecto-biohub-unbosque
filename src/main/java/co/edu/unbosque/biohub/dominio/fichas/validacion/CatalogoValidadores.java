package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * Reúne una estrategia por tipo de dato. La ficha le pide "el validador de NUMERO"
 * sin saber cuál clase concreta es.
 */
public class CatalogoValidadores {

    private final Map<TipoDato, ValidadorDeTipo> porTipo = new EnumMap<>(TipoDato.class);

    public CatalogoValidadores(List<ValidadorDeTipo> validadores) {
        validadores.forEach(v -> porTipo.put(v.tipo(), v));
        for (TipoDato tipo : TipoDato.values()) {
            if (!porTipo.containsKey(tipo)) {
                throw new IllegalStateException("Falta el validador del tipo " + tipo);
            }
        }
    }

    /** Las seis estrategias iniciales (P-32). */
    public static CatalogoValidadores estandar() {
        return new CatalogoValidadores(List.of(
                new ValidadorTexto(),
                new ValidadorNumero(),
                new ValidadorFecha(),
                new ValidadorLista(),
                new ValidadorRutaGps(),
                new ValidadorArchivo()));
    }

    public ValidadorDeTipo para(TipoDato tipo) {
        return porTipo.get(tipo);
    }
}
