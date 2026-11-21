package co.edu.unbosque.biohub.aplicacion.comun;

/** Lo pedido no existe. La presentación lo traduce a HTTP 404. */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
