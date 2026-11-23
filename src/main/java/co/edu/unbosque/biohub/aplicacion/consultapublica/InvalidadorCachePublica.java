package co.edu.unbosque.biohub.aplicacion.consultapublica;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.biohub.persistencia.cache.AlmacenCache;

import jakarta.annotation.PreDestroy;

/**
 * Borra de la caché pública lo que una escritura dejó viejo (T-07, P-14, PR-16).
 * Lo usan: el cambio de una ficha (secuencia 7), el registro de un hallazgo (secuencia 1,
 * paso 18) y la reclasificación (secuencia 3, pasos 20 y 21).
 *
 * Borra DESPUÉS del commit: si borrara antes, una consulta pública que llegue
 * entre el borrado y el commit volvería a guardar el dato viejo.
 *
 * Y borra DOS veces. El portal lee de la réplica pública, que va unos milisegundos detrás
 * de la primaria (consistencia eventual). Si una consulta llega justo después del primer
 * borrado, puede leer la réplica todavía vieja y volver a guardar el dato viejo. El segundo
 * borrado, un momento después, lo quita ("doble borrado con retraso").
 *
 * Si un borrado falla (Redis caído), no pasa nada grave: la entrada vence sola
 * en pocos segundos (ver LectorPublico).
 */
@Component
public class InvalidadorCachePublica {

    private final AlmacenCache cache;
    private final long segundoBorradoMs;
    private final ScheduledExecutorService programador;

    /** Sin segundo borrado. Para pruebas unitarias. */
    public InvalidadorCachePublica(AlmacenCache cache) {
        this(cache, 0);
    }

    @Autowired
    public InvalidadorCachePublica(AlmacenCache cache,
                                   @Value("${biohub.cache.segundo-borrado-ms:500}") long segundoBorradoMs) {
        this.cache = cache;
        this.segundoBorradoMs = segundoBorradoMs;
        this.programador = segundoBorradoMs > 0 ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "segundo-borrado-cache");
            hilo.setDaemon(true);
            return hilo;
        }) : null;
    }

    /** La ficha o los datos de la especie cambiaron: se borra su ficha y el listado. */
    public void fichaCambio(long especieId) {
        borrarDespuesDelCommit(LectorPublico.claveFicha(especieId), LectorPublico.CLAVE_LISTADO);
    }

    /**
     * Cambiaron los hallazgos de estas especies (uno nuevo, o uno que pasó de una a otra).
     * La ficha pública muestra en qué departamentos hay hallazgos y cuántos, así que se
     * borra la ficha de cada una.
     *
     * El listado también muestra el total de hallazgos por especie, pero a propósito NO se
     * borra aquí: en el pico se registran varios hallazgos por segundo, y recalcular el
     * listado completo cada vez le quitaría el sentido a la caché. Su copia vence sola en
     * 3 s, así que el total se actualiza en ese tiempo, dentro de PR-16 (≤ 5 s).
     */
    public void hallazgosCambiaron(long... especies) {
        borrarDespuesDelCommit(Arrays.stream(especies).distinct()
                .mapToObj(LectorPublico::claveFicha).toArray(String[]::new));
    }

    private void borrarDespuesDelCommit(String... claves) {
        Runnable borrar = () -> borrar(claves);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar.run();
                }
            });
        } else {
            borrar.run();
        }
    }

    private void borrar(String... claves) {
        cache.borrar(claves);
        if (programador != null) {
            programador.schedule(() -> cache.borrar(claves), segundoBorradoMs, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    void detener() {
        if (programador != null) {
            programador.shutdownNow();
        }
    }
}
