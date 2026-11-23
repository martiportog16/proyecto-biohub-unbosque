package co.edu.unbosque.biohub.dominio.integracion;

import java.util.ArrayList;
import java.util.List;

/** Una universidad u ONG que consume la API externa /v1 (RF-12, RF-13). */
public record Consumidor(long id, String nombre, int limitePorMinuto, boolean activo) {

    /** Límites permitidos. S-05: el límite inicial acordado es 100 por minuto, y es ajustable. */
    public static final int LIMITE_MINIMO = 1;
    public static final int LIMITE_MAXIMO = 10_000;

    /** Reglas de un cambio de límite. Reúne todos los errores. */
    public static List<String> erroresDeCambio(Integer limitePorMinuto, Boolean activo) {
        List<String> errores = new ArrayList<>();
        if (limitePorMinuto == null && activo == null) {
            errores.add("Envíe limitePorMinuto, activo o ambos");
        }
        if (limitePorMinuto != null && (limitePorMinuto < LIMITE_MINIMO || limitePorMinuto > LIMITE_MAXIMO)) {
            errores.add("limitePorMinuto: debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
        }
        return errores;
    }

    /** S-05: si el administrador no dice otra cosa, un consumidor nuevo empieza con 100 por minuto. */
    public static final int LIMITE_INICIAL = 100;

    /** Reglas para dar de alta un consumidor nuevo. Reúne todos los errores. */
    public static List<String> erroresDeAlta(String nombre, Integer limitePorMinuto) {
        List<String> errores = new ArrayList<>();
        if (nombre == null || nombre.isBlank()) {
            errores.add("nombre: es obligatorio");
        } else if (nombre.trim().length() < 3 || nombre.trim().length() > 120) {
            errores.add("nombre: debe tener entre 3 y 120 caracteres");
        }
        if (limitePorMinuto != null && (limitePorMinuto < LIMITE_MINIMO || limitePorMinuto > LIMITE_MAXIMO)) {
            errores.add("limitePorMinuto: debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
        }
        return errores;
    }

    public Consumidor con(Integer nuevoLimite, Boolean nuevoActivo) {
        return new Consumidor(id, nombre, nuevoLimite == null ? limitePorMinuto : nuevoLimite,
                nuevoActivo == null ? activo : nuevoActivo);
    }
}
