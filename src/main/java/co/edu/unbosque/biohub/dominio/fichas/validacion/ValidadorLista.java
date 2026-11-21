package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/** Un valor que debe estar entre las opciones de la definición. */
public class ValidadorLista implements ValidadorDeTipo {

    @Override
    public TipoDato tipo() {
        return TipoDato.LISTA;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof String opcion) || !definicion.opciones().contains(opcion)) {
            return Optional.of("debe ser una de estas opciones: " + String.join(", ", definicion.opciones()));
        }
        return Optional.empty();
    }
}
