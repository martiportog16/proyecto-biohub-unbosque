package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/** Texto libre de 1 a 500 caracteres (también el código de una muestra de ADN). */
public class ValidadorTexto implements ValidadorDeTipo {

    static final int MAXIMO = 500;

    @Override
    public TipoDato tipo() {
        return TipoDato.TEXTO;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof String texto)) {
            return Optional.of("debe ser texto");
        }
        if (texto.isBlank()) {
            return Optional.of("no puede estar vacío");
        }
        if (texto.length() > MAXIMO) {
            return Optional.of("no puede tener más de " + MAXIMO + " caracteres");
        }
        return Optional.empty();
    }
}
