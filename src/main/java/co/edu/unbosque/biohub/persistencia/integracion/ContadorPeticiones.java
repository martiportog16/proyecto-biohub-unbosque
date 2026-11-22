package co.edu.unbosque.biohub.persistencia.integracion;

/**
 * Cuenta las peticiones de cada consumidor en cada minuto (ventana fija).
 * Vive en Redis para que TODAS las instancias públicas compartan el mismo conteo:
 * si cada instancia contara por su lado, con 2 instancias el límite real sería el doble.
 */
public interface ContadorPeticiones {

    /** Suma 1 a la cuenta del consumidor en ese minuto y devuelve el total. */
    long incrementar(long consumidorId, long minutoEpoch);
}
