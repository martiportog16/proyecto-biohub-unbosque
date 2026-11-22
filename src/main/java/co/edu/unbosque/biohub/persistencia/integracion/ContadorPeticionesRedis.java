package co.edu.unbosque.biohub.persistencia.integracion;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContadorPeticionesRedis implements ContadorPeticiones {

    private static final Duration VIDA_DE_LA_CUENTA = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;

    public ContadorPeticionesRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementar(long consumidorId, long minutoEpoch) {
        String llave = "limite:" + consumidorId + ":" + minutoEpoch;
        Long total = redis.opsForValue().increment(llave);
        if (total != null && total == 1L) {
            redis.expire(llave, VIDA_DE_LA_CUENTA);   // la cuenta se borra sola al pasar el minuto
        }
        return total == null ? 0 : total;
    }
}
