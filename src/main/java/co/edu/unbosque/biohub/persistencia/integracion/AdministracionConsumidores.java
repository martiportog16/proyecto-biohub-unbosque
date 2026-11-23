package co.edu.unbosque.biohub.persistencia.integracion;

import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.integracion.Consumidor;

/**
 * Lo que necesita el ADMINISTRADOR de la plataforma (HU-10, CU-12).
 *
 * Está separada de RepositorioConsumidores a propósito (segregación de interfaces):
 * el filtro de la API externa, que corre en cada petición, solo puede BUSCAR por
 * huella; no tiene cómo cambiar un límite. La misma clase JDBC implementa las dos.
 */
public interface AdministracionConsumidores {

    Optional<Consumidor> buscar(long id);

    List<Consumidor> listar();

    /** Cambia el límite y si está activo. La API key nunca se cambia por aquí. */
    void actualizar(Consumidor consumidor);

    boolean existeNombre(String nombre);

    /** Da de alta un consumidor. Recibe la HUELLA de la API key, nunca la key. Devuelve el id. */
    long crear(String nombre, String huellaApiKey, int limitePorMinuto);
}
