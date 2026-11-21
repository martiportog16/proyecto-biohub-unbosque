package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * Patrón STRATEGY (adoptado en la Entrega 2, M-2 aprobado).
 *
 * Cada tipo de dato tiene su propia estrategia de validación. La ficha no usa un
 * "if/switch" que crece con cada tipo: elige la estrategia según el tipo del atributo.
 * Principio O de SOLID (S18): un tipo nuevo = una clase nueva, sin tocar las demás.
 */
public interface ValidadorDeTipo {

    TipoDato tipo();

    /** Devuelve el motivo del error, o vacío si el valor es válido. El valor nunca es null. */
    Optional<String> validar(DefinicionAtributo definicion, Object valor);
}
