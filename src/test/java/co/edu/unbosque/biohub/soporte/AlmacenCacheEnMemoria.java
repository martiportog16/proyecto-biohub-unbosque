package co.edu.unbosque.biohub.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import co.edu.unbosque.biohub.persistencia.cache.AlmacenCache;

/**
 * FAKE de la caché (S20): funciona de verdad, pero en memoria y con un reloj inyectado,
 * así se puede probar el vencimiento sin esperar. Es la segunda implementación de
 * AlmacenCache, la que demuestra que la aplicación no depende de Redis (PR-17).
 */
public class AlmacenCacheEnMemoria implements AlmacenCache {

    private record Entrada(String valor, Instant vence) {
    }

    private final Map<String, Entrada> datos = new HashMap<>();
    private final Clock reloj;

    public AlmacenCacheEnMemoria(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public Optional<String> leer(String clave) {
        Entrada e = datos.get(clave);
        if (e == null || !reloj.instant().isBefore(e.vence())) {
            datos.remove(clave);
            return Optional.empty();
        }
        return Optional.of(e.valor());
    }

    @Override
    public void guardar(String clave, String valor, Duration vida) {
        datos.put(clave, new Entrada(valor, reloj.instant().plus(vida)));
    }

    @Override
    public void borrar(String... claves) {
        for (String c : claves) {
            datos.remove(c);
        }
    }

    public boolean contiene(String clave) {
        return leer(clave).isPresent();
    }
}
