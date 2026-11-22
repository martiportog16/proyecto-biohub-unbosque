package co.edu.unbosque.biohub.aplicacion.consultapublica;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.biohub.soporte.AlmacenCacheEnMemoria;

/** Cuándo se borra la caché pública, sin base de datos ni Redis. */
class InvalidadorCachePublicaTest {

    private final AlmacenCacheEnMemoria cache = new AlmacenCacheEnMemoria(Clock.systemUTC());
    private final InvalidadorCachePublica invalidador = new InvalidadorCachePublica(cache);

    private void llenarCache() {
        cache.guardar(LectorPublico.claveFicha(7L), "ficha 7", Duration.ofMinutes(1));
        cache.guardar(LectorPublico.claveFicha(8L), "ficha 8", Duration.ofMinutes(1));
        cache.guardar(LectorPublico.CLAVE_LISTADO, "listado", Duration.ofMinutes(1));
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sinTransaccionBorraDeInmediatoLaFichaYElListado() {
        llenarCache();

        invalidador.fichaCambio(7L);

        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).isFalse();
        assertThat(cache.contiene(LectorPublico.CLAVE_LISTADO)).isFalse();
        assertThat(cache.contiene(LectorPublico.claveFicha(8L))).as("otras fichas no se tocan").isTrue();
    }

    @Test
    void dentroDeUnaTransaccionEsperaAlCommit() {
        llenarCache();
        TransactionSynchronizationManager.initSynchronization();

        invalidador.fichaCambio(7L);
        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).as("antes del commit sigue").isTrue();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).as("después del commit se borra").isFalse();
    }

    @Test
    void siLaTransaccionSeDeshaceNoBorraNada() {
        llenarCache();
        TransactionSynchronizationManager.initSynchronization();

        invalidador.fichaCambio(7L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).isTrue();
    }

    @Test
    void cuandoCambianHallazgosBorraSoloLasFichasDeEsasEspecies() {
        llenarCache();

        invalidador.hallazgosCambiaron(7L, 8L, 7L);

        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).isFalse();
        assertThat(cache.contiene(LectorPublico.claveFicha(8L))).isFalse();
        assertThat(cache.contiene(LectorPublico.CLAVE_LISTADO)).as("el listado no tiene conteos").isTrue();
    }

    @Test
    void borraOtraVezUnMomentoDespuesPorSiLaReplicaIbaAtrasada() throws InterruptedException {
        InvalidadorCachePublica conSegundoBorrado = new InvalidadorCachePublica(cache, 100);

        conSegundoBorrado.hallazgosCambiaron(7L);
        // Una consulta pública leyó la réplica todavía vieja y volvió a guardar la ficha
        cache.guardar(LectorPublico.claveFicha(7L), "ficha vieja otra vez", Duration.ofMinutes(1));
        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).isTrue();

        long limite = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (cache.contiene(LectorPublico.claveFicha(7L)) && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
        assertThat(cache.contiene(LectorPublico.claveFicha(7L))).as("el segundo borrado la quitó").isFalse();
        conSegundoBorrado.detener();
    }
}
