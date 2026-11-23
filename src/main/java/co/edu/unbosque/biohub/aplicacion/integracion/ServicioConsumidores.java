package co.edu.unbosque.biohub.aplicacion.integracion;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.integracion.Consumidor;
import co.edu.unbosque.biohub.persistencia.integracion.AdministracionConsumidores;

/**
 * El administrador de la plataforma ajusta los límites de los consumidores
 * mientras el sistema corre (HU-10 criterio 4, CU-12, P-10).
 *
 * Por esto el límite está en la app y no en Nginx: la configuración de Nginx es fija.
 * El cambio se guarda en la primaria y el grupo público lo ve en su réplica en ≤ 5 s (P-14).
 */
@Service
public class ServicioConsumidores {

    public record CambioConsumidor(Integer limitePorMinuto, Boolean activo) {
    }

    /** Lo que ve el administrador. A propósito no incluye la API key ni su huella. */
    public record VistaConsumidor(long id, String nombre, int limitePorMinuto, boolean activo) {

        static VistaConsumidor de(Consumidor c) {
            return new VistaConsumidor(c.id(), c.nombre(), c.limitePorMinuto(), c.activo());
        }
    }

    public record NuevoConsumidor(String nombre, Integer limitePorMinuto) {
    }

    /**
     * Lo que se devuelve UNA sola vez al dar de alta: la API key en claro. BioHub solo
     * guarda su huella SHA-256, así que si se pierde no se puede recuperar.
     */
    public record ConsumidorCreado(VistaConsumidor consumidor, String apiKey) {
    }

    private static final SecureRandom AZAR = new SecureRandom();

    private final AdministracionConsumidores consumidores;

    public ServicioConsumidores(AdministracionConsumidores consumidores) {
        this.consumidores = consumidores;
    }

    public List<VistaConsumidor> listar() {
        return consumidores.listar().stream().map(VistaConsumidor::de).toList();
    }

    /** Alta de una universidad u ONG (RF-12, HU-09). Antes había que hacerlo con SQL. */
    @Transactional
    public ConsumidorCreado crear(NuevoConsumidor datos) {
        List<String> errores = new ArrayList<>(Consumidor.erroresDeAlta(datos.nombre(), datos.limitePorMinuto()));
        if (errores.isEmpty() && consumidores.existeNombre(datos.nombre().trim())) {
            errores.add("nombre: ya existe un consumidor llamado '" + datos.nombre().trim() + "'");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El consumidor no es válido", errores);
        }
        String apiKey = nuevaApiKey();
        int limite = datos.limitePorMinuto() == null ? Consumidor.LIMITE_INICIAL : datos.limitePorMinuto();
        long id = consumidores.crear(datos.nombre().trim(), ServicioLimitePeticiones.huella(apiKey), limite);
        return new ConsumidorCreado(new VistaConsumidor(id, datos.nombre().trim(), limite, true), apiKey);
    }

    /** 32 bytes al azar (256 bits) en Base64 sin relleno: imposible de adivinar. */
    static String nuevaApiKey() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return "bh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public VistaConsumidor cambiar(long id, CambioConsumidor cambio) {
        List<String> errores = Consumidor.erroresDeCambio(cambio.limitePorMinuto(), cambio.activo());
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El cambio no es válido", errores);
        }
        Consumidor actual = consumidores.buscar(id)
                .orElseThrow(() -> new NoEncontradoException("No existe el consumidor " + id));
        Consumidor nuevo = actual.con(cambio.limitePorMinuto(), cambio.activo());
        consumidores.actualizar(nuevo);
        return VistaConsumidor.de(nuevo);
    }
}
