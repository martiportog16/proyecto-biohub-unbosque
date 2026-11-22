package co.edu.unbosque.biohub.aplicacion.consultapublica;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.biohub.persistencia.cache.AlmacenCache;

/**
 * Borra de la caché pública lo que una escritura dejó viejo (P-14, PR-16).
 *
 * Borra DESPUÉS del commit: si borrara antes, una consulta pública que llegue
 * entre el borrado y el commit volvería a guardar el dato viejo.
 *
 * Si el borrado falla (Redis caído), no pasa nada grave: la entrada vence sola
 * en pocos segundos (ver LectorPublico).
 */
@Component
public class InvalidadorCachePublica {

    private final AlmacenCache cache;

    public InvalidadorCachePublica(AlmacenCache cache) {
        this.cache = cache;
    }

    /** La ficha o los datos de la especie cambiaron: se borra su ficha y el listado. */
    public void fichaCambio(long especieId) {
        Runnable borrar = () -> cache.borrar(LectorPublico.claveFicha(especieId), LectorPublico.CLAVE_LISTADO);
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
}
