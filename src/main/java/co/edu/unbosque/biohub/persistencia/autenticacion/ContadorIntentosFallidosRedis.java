package co.edu.unbosque.biohub.persistencia.autenticacion;

import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Los intentos fallidos van en Redis, compartidos por las 2 instancias científicas: si
 * cada instancia contara por su lado, un atacante tendría el doble de intentos.
 *
 * Si Redis no responde, NO se bloquea a nadie (se sigue con la cuenta en 0): es preferible
 * que el personal pueda entrar. El límite por IP de Nginx sigue protegiendo.
 */
@Repository
public class ContadorIntentosFallidosRedis implements ContadorIntentosFallidos {

    static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final Logger LOG = LoggerFactory.getLogger(ContadorIntentosFallidosRedis.class);

    private final StringRedisTemplate redis;

    public ContadorIntentosFallidosRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long fallos(String correo) {
        try {
            String valor = redis.opsForValue().get(llave(correo));
            return valor == null ? 0 : Long.parseLong(valor);
        } catch (DataAccessException e) {
            LOG.warn("Redis no responde al leer intentos fallidos: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void registrarFallo(String correo) {
        try {
            Long total = redis.opsForValue().increment(llave(correo));
            if (total != null && total == 1L) {
                redis.expire(llave(correo), BLOQUEO);
            }
        } catch (DataAccessException e) {
            LOG.warn("Redis no responde al registrar un intento fallido: {}", e.getMessage());
        }
    }

    @Override
    public void limpiar(String correo) {
        try {
            redis.delete(llave(correo));
        } catch (DataAccessException e) {
            LOG.warn("Redis no responde al limpiar intentos fallidos: {}", e.getMessage());
        }
    }

    private static String llave(String correo) {
        return "login:fallos:" + (correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT));
    }
}
