package co.edu.unbosque.biohub.persistencia.autenticacion;

/**
 * Cuántas veces seguidas se equivocó la clave de un correo (protección contra fuerza bruta, T-17).
 * La cuenta vence sola: el bloqueo dura un tiempo y luego se puede volver a intentar.
 */
public interface ContadorIntentosFallidos {

    long fallos(String correo);

    void registrarFallo(String correo);

    void limpiar(String correo);
}
