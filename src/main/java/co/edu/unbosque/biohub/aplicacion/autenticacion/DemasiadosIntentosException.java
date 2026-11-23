package co.edu.unbosque.biohub.aplicacion.autenticacion;

/** Un correo se equivocó demasiadas veces seguidas: se bloquea un rato (429). */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(long segundosDeEspera) {
        super("Demasiados intentos fallidos con este correo. Espere " + (segundosDeEspera / 60)
                + " minutos o pida ayuda al administrador.");
        this.segundosDeEspera = segundosDeEspera;
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }
}
