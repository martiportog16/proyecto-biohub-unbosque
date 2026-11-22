package co.edu.unbosque.biohub.persistencia.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter: convierte las operaciones de AlmacenCache en comandos de Redis
 * (GET, SET con EX, DEL).
 *
 * Si Redis no responde, NO hace fallar la consulta: se comporta como si la clave
 * no estuviera y el lector va a la base de datos. La caché acelera, pero el portal
 * no depende de ella para funcionar.
 */
@Component
public class AdaptadorRedis implements AlmacenCache {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptadorRedis.class);

    private final StringRedisTemplate redis;

    public AdaptadorRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> leer(String clave) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(clave));
        } catch (DataAccessException e) {
            LOG.warn("Caché no disponible al leer {}: se consulta la base de datos", clave);
            return Optional.empty();
        }
    }

    @Override
    public void guardar(String clave, String valor, Duration vida) {
        try {
            redis.opsForValue().set(clave, valor, vida);
        } catch (DataAccessException e) {
            LOG.warn("Caché no disponible al guardar {}", clave);
        }
    }

    @Override
    public void borrar(String... claves) {
        try {
            redis.delete(List.of(claves));
        } catch (DataAccessException e) {
            LOG.warn("Caché no disponible al borrar {}: vencerá sola", List.of(claves));
        }
    }
}
