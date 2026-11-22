package co.edu.unbosque.biohub.dominio.integracion;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * Qué valores de un hallazgo puede recibir una universidad u ONG (P-30).
 *
 * Solo los de tipo TEXTO, NUMERO, FECHA y LISTA. Se quitan:
 *   - RUTA_GPS: es una lista de coordenadas. Entregarla sería entregar la ubicación
 *     exacta por la puerta de atrás (RNF-06).
 *   - ARCHIVO: una foto o grabación puede traer la ubicación dentro (metadatos GPS).
 *   - Cualquier valor que la ficha no defina: lo que no se conoce, no sale.
 *
 * Se usa la ficha EN LA VERSIÓN con que se registró el hallazgo, así un atributo
 * retirado después sigue saliendo con su tipo correcto.
 */
public final class AtributosPublicos {

    public static final Set<TipoDato> TIPOS_PUBLICOS =
            EnumSet.of(TipoDato.TEXTO, TipoDato.NUMERO, TipoDato.FECHA, TipoDato.LISTA);

    private AtributosPublicos() {
    }

    public static Map<String, Object> filtrar(FichaTaxonomica ficha, Map<String, Object> valores) {
        Map<String, Object> publicos = new LinkedHashMap<>();
        for (DefinicionAtributo definicion : ficha.atributos()) {
            Object valor = valores.get(definicion.nombre());
            if (valor != null && TIPOS_PUBLICOS.contains(definicion.tipo())) {
                publicos.put(definicion.nombre(), valor);
            }
        }
        return publicos;
    }
}
