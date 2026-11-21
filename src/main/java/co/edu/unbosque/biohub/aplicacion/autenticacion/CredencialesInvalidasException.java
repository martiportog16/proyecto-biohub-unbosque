package co.edu.unbosque.biohub.aplicacion.autenticacion;

/**
 * Correo o clave incorrectos, o usuario inactivo.
 * El mensaje es el mismo en los tres casos para no revelar qué correos existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Correo o clave incorrectos");
    }
}
