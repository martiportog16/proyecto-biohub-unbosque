package co.edu.unbosque.biohub.dominio.fichas;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Un atributo de la ficha de una especie: su nombre, su tipo y si es obligatorio.
 * Retirar un atributo NO lo borra: lo marca inactivo (los valores viejos se conservan).
 */
public record DefinicionAtributo(
        String nombre,
        TipoDato tipo,
        boolean obligatorio,
        boolean activo,
        List<String> opciones,
        String unidad) {

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[a-z][a-z0-9_]{1,49}");

    public DefinicionAtributo {
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
    }

    public DefinicionAtributo retirado() {
        return new DefinicionAtributo(nombre, tipo, obligatorio, false, opciones, unidad);
    }

    /** Revisa que la definición en sí tenga sentido antes de agregarla a la ficha. */
    public List<String> erroresDeDefinicion() {
        List<String> errores = new ArrayList<>();
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            errores.add("nombre: debe ir en minúsculas, sin espacios, entre 2 y 50 caracteres (ej. temperatura_agua)");
        }
        if (tipo == null) {
            errores.add("tipo: es obligatorio");
        }
        if (tipo == TipoDato.LISTA && opciones.size() < 2) {
            errores.add("opciones: un atributo de tipo LISTA necesita al menos 2 opciones");
        }
        if (tipo != TipoDato.LISTA && !opciones.isEmpty()) {
            errores.add("opciones: solo los atributos de tipo LISTA llevan opciones");
        }
        return errores;
    }
}
