package co.edu.unbosque.biohub.configuracion;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj del sistema como dependencia inyectada.
 * En las pruebas se reemplaza por un reloj fijo (stub, S21) para que las reglas
 * con fechas ("no puede ser futura") den siempre el mismo resultado.
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
