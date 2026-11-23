package co.edu.unbosque.biohub.dominio.integracion;

import java.util.Map;

import co.edu.unbosque.biohub.dominio.fichas.DatosDeUbicacion;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;

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

    private AtributosPublicos() {
    }

    /** Lo mismo que ve el personal científico sin autorización: los datos que no son de ubicación. */
    public static Map<String, Object> filtrar(FichaTaxonomica ficha, Map<String, Object> valores) {
        return DatosDeUbicacion.separar(ficha, valores).visibles();
    }
}
