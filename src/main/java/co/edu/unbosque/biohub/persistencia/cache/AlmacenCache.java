package co.edu.unbosque.biohub.persistencia.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * La caché que usa BioHub, con SOLO las operaciones que necesita (patrón Adapter).
 *
 * La aplicación depende de esta interfaz y no de Redis. Así:
 *  - en producción la implementa AdaptadorRedis (traduce estas llamadas a la API de Redis);
 *  - en las pruebas unitarias se usa un fake en memoria (PR-17);
 *  - si un día se cambia Redis por otra caché, solo se escribe otro adaptador.
 */
public interface AlmacenCache {

    /** El valor guardado, o vacío si no está, ya venció, o la caché no responde. */
    Optional<String> leer(String clave);

    /** Guarda el valor; se borra solo cuando pasa "vida". */
    void guardar(String clave, String valor, Duration vida);

    void borrar(String... claves);
}
