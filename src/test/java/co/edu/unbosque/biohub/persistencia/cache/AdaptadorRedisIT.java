package co.edu.unbosque.biohub.persistencia.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import co.edu.unbosque.biohub.soporte.BaseIT;

/** El adaptador contra un Redis REAL (S21: el mismo motor que en producción). */
class AdaptadorRedisIT extends BaseIT {

    @Autowired
    AdaptadorRedis cache;

    private final String clave = "prueba:" + System.nanoTime();

    @Test
    void guardaYLeeUnValor() {
        cache.guardar(clave, "{\"id\":1}", Duration.ofMinutes(1));

        assertThat(cache.leer(clave)).contains("{\"id\":1}");
    }

    @Test
    void borrarQuitaElValor() {
        cache.guardar(clave, "x", Duration.ofMinutes(1));

        cache.borrar(clave);

        assertThat(cache.leer(clave)).isEmpty();
    }

    @Test
    void elValorVenceSolo() throws InterruptedException {
        cache.guardar(clave, "x", Duration.ofMillis(300));

        Thread.sleep(600);

        assertThat(cache.leer(clave)).isEmpty();
    }

    @Test
    void siRedisNoRespondeSeComportaComoUnaClaveVacia() {
        // Un Redis que no existe (puerto 1): el adaptador no debe lanzar excepción
        LettuceConnectionFactory caido = new LettuceConnectionFactory("localhost", 1);
        caido.afterPropertiesSet();
        AdaptadorRedis sinRedis = new AdaptadorRedis(new StringRedisTemplate(caido));

        assertThat(sinRedis.leer(clave)).isEmpty();
        sinRedis.guardar(clave, "x", Duration.ofSeconds(1));
        sinRedis.borrar(clave);
        caido.destroy();
    }
}
