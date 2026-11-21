package co.edu.unbosque.biohub.dominio.fichas.validacion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;

/**
 * Una referencia a un archivo (grabación acústica, foto) guardado fuera de la base,
 * por ejemplo "grabaciones/2026/canto-031.wav". BioHub guarda la referencia, no el archivo.
 */
public class ValidadorArchivo implements ValidadorDeTipo {

    static final int MAXIMO = 300;

    @Override
    public TipoDato tipo() {
        return TipoDato.ARCHIVO;
    }

    @Override
    public Optional<String> validar(DefinicionAtributo definicion, Object valor) {
        if (!(valor instanceof String ruta) || ruta.isBlank()) {
            return Optional.of("debe ser la referencia (ruta o URL) del archivo");
        }
        if (ruta.length() > MAXIMO) {
            return Optional.of("la referencia no puede tener más de " + MAXIMO + " caracteres");
        }
        if (!ruta.contains(".")) {
            return Optional.of("la referencia debe incluir la extensión del archivo (ej. .wav, .jpg)");
        }
        return Optional.empty();
    }
}
