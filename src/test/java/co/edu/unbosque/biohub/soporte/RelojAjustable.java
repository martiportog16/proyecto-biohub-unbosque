package co.edu.unbosque.biohub.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj de prueba que se puede ADELANTAR (S21: stub del reloj).
 * Sirve para simular cambios hechos en días distintos sin esperar días de verdad.
 */
public class RelojAjustable extends Clock {

    private final ZoneId zona;
    private volatile Instant ahora;

    public RelojAjustable(Instant inicio, ZoneId zona) {
        this.ahora = inicio;
        this.zona = zona;
    }

    public void adelantar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojAjustable(ahora, otraZona);
    }
}
