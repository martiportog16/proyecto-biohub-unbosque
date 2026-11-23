package co.edu.unbosque.biohub.aplicacion.comun;

/**
 * El usuario tiene acceso a la operación, pero no sobre ESE caso.
 * Ejemplo: el administrador de la plataforma puede cambiar roles, pero no dar el de alto
 * rango, que le corresponde a un director (S-01). La presentación lo traduce a HTTP 403,
 * con el motivo en español.
 */
public class SinPermisoException extends RuntimeException {

    public SinPermisoException(String mensaje) {
        super(mensaje);
    }
}
