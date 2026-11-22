package co.edu.unbosque.biohub.aplicacion.historial;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.ConflictoException;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.InvalidadorCachePublica;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.NuevaReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaReclasificacion;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.dominio.historial.HistorialHallazgo;
import co.edu.unbosque.biohub.dominio.historial.Reclasificacion;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;
import co.edu.unbosque.biohub.persistencia.historial.RepositorioHistorial;

/**
 * Caso de uso: reclasificar un hallazgo (HU-06, CU-06).
 *
 * 1. Valida el motivo (obligatorio) y la especie nueva (debe existir y ser distinta).
 * 2. Agrega el cambio al historial del dominio, que verifica que la cadena no se rompa.
 * 3. Guarda la reclasificación y la especie nueva del hallazgo juntas.
 *
 * Quién puede reclasificar (solo el director científico) se revisa en el controlador
 * con @PreAuthorize (P-27).
 */
@Service
public class ServicioReclasificacion {

    private final RepositorioHallazgos hallazgos;
    private final RepositorioHistorial historial;
    private final RepositorioFichas fichas;
    private final NombresHistorial nombres;
    private final Clock reloj;
    private final InvalidadorCachePublica cachePublica;

    public ServicioReclasificacion(RepositorioHallazgos hallazgos, RepositorioHistorial historial,
                                   RepositorioFichas fichas, NombresHistorial nombres, Clock reloj,
                                   InvalidadorCachePublica cachePublica) {
        this.hallazgos = hallazgos;
        this.historial = historial;
        this.fichas = fichas;
        this.nombres = nombres;
        this.reloj = reloj;
        this.cachePublica = cachePublica;
    }

    @Transactional
    public VistaReclasificacion reclasificar(long hallazgoId, NuevaReclasificacion datos, long autorId) {
        Hallazgo hallazgo = hallazgos.buscar(hallazgoId)
                .orElseThrow(() -> new NoEncontradoException("No existe el hallazgo " + hallazgoId));

        List<String> errores = new ArrayList<>(
                Reclasificacion.errores(hallazgo.especieId(), datos.especieNuevaId(), datos.motivo()));
        if (datos.especieNuevaId() != null && fichas.especie(datos.especieNuevaId()).isEmpty()) {
            errores.add("especieNuevaId: no existe la especie " + datos.especieNuevaId());
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("La reclasificación no es válida", errores);
        }

        Reclasificacion nueva = new Reclasificacion(null, hallazgoId, hallazgo.especieId(), datos.especieNuevaId(),
                datos.motivo().strip(), autorId, Instant.now(reloj));
        HistorialHallazgo.de(hallazgo, historial.deHallazgo(hallazgoId)).agregar(nueva);

        long id = historial.registrar(nueva)
                .orElseThrow(() -> new ConflictoException(
                        "Otra persona reclasificó este hallazgo al mismo tiempo. Consulta el historial y vuelve a intentarlo."));

        // Secuencia 3, pasos 20 y 21: el hallazgo sale de la ficha pública de una especie y entra en la de otra
        cachePublica.hallazgosCambiaron(nueva.especieAnteriorId(), nueva.especieNuevaId());

        return nombres.traductor().vista(new Reclasificacion(id, nueva.hallazgoId(), nueva.especieAnteriorId(),
                nueva.especieNuevaId(), nueva.motivo(), nueva.autorId(), nueva.realizadaEn()));
    }
}
