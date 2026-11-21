package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/** Una fecha real en formato ISO (año-mes-día). 2026-02-29 no existe y se rechaza. */
public class ValidadorFecha implements ValidadorDeTipo {

    @Override
    public TipoDato tipo() {
        return TipoDato.FECHA;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof String texto)) {
            return Optional.of("debe ser una fecha con formato AAAA-MM-DD");
        }
        try {
            LocalDate.parse(texto);
            return Optional.empty();
        } catch (DateTimeParseException e) {
            return Optional.of("no es una fecha válida con formato AAAA-MM-DD");
        }
    }
}
