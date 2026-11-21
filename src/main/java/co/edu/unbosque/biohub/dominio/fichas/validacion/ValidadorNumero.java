package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/** Un número (entero o decimal). La unidad, por ejemplo °C, vive en la definición. */
public class ValidadorNumero implements ValidadorDeTipo {

    @Override
    public TipoDato tipo() {
        return TipoDato.NUMERO;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof Number numero)) {
            return Optional.of("debe ser un número");
        }
        double d = numero.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return Optional.of("debe ser un número finito");
        }
        return Optional.empty();
    }
}
