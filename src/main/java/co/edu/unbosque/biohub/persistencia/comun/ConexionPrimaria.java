package co.edu.unbosque.biohub.persistencia.comun;

import java.util.function.Supplier;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/**
 * La conexión a la base PRIMARIA (la única donde se escribe).
 *
 * - En los grupos científico y público es la misma conexión de siempre.
 * - En el batch es una SEGUNDA conexión: su conexión principal apunta a la réplica
 *   de analítica (lectura pesada) y esta apunta a la primaria (guardar el informe). P-03.
 *
 * Quien necesite escribir "pase lo que pase en la primaria" pide esta clase en vez
 * del JdbcClient normal. FuentesDeDatosConfig decide cuál de las dos versiones crear.
 */
public class ConexionPrimaria implements AutoCloseable {

    private final JdbcClient jdbc;
    private final TransactionOperations transacciones;
    private final AutoCloseable recurso;

    public ConexionPrimaria(JdbcClient jdbc, TransactionOperations transacciones, AutoCloseable recurso) {
        this.jdbc = jdbc;
        this.transacciones = transacciones;
        this.recurso = recurso;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    /** Ejecuta todo dentro de UNA transacción en la primaria: o se guarda todo, o nada. */
    public <T> T enTransaccion(Supplier<T> trabajo) {
        return transacciones.execute(estado -> trabajo.get());
    }

    /** Spring lo llama al apagar: cierra el grupo de conexiones propio del batch, si lo hay. */
    @Override
    public void close() throws Exception {
        if (recurso != null) {
            recurso.close();
        }
    }
}
