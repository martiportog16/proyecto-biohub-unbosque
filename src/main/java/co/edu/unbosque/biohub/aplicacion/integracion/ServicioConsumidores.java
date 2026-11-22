package co.edu.unbosque.biohub.aplicacion.integracion;

import java.util.List;

import org.springframework.stereotype.Service;

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

    private final AdministracionConsumidores consumidores;

    public ServicioConsumidores(AdministracionConsumidores consumidores) {
        this.consumidores = consumidores;
    }

    public List<VistaConsumidor> listar() {
        return consumidores.listar().stream().map(VistaConsumidor::de).toList();
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
