package co.edu.unbosque.biohub.aplicacion.comun;

/**
 * La operación choca con un cambio que otra persona hizo al mismo tiempo (HTTP 409).
 * Quien llama puede volver a consultar y reintentar.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
