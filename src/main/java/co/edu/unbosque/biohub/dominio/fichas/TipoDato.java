package co.edu.unbosque.biohub.dominio.fichas;

/**
 * Tipos de dato iniciales de un atributo de ficha (P-32), sacados del Enunciado.
 * Agregar un ATRIBUTO de uno de estos tipos es solo datos (RNF-01).
 * Agregar un TIPO nuevo exige un validador nuevo (Strategy) y un despliegue.
 */
public enum TipoDato {
    TEXTO,      // incluye el código de una muestra de ADN
    NUMERO,     // con unidad opcional, por ejemplo temperatura en °C
    FECHA,      // formato ISO: 2026-11-15
    LISTA,      // un valor entre opciones definidas
    RUTA_GPS,   // lista de puntos {lat, lon}
    ARCHIVO     // referencia a una grabación o foto; el archivo no se guarda en la base
}
