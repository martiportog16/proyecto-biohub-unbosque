package co.edu.unbosque.biohub.aplicacion.autorizacion;

/** La autorización negó ver coordenadas exactas (HTTP 403). El intento ya quedó registrado. */
public class AccesoCoordenadasDenegadoException extends RuntimeException {

    public AccesoCoordenadasDenegadoException(String mensaje) {
        super(mensaje);
    }
}
