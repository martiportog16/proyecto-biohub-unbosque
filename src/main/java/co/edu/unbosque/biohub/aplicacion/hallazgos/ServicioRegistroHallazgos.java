package co.edu.unbosque.biohub.aplicacion.hallazgos;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.NuevoHallazgo;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.VistaHallazgo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;
import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.persistencia.coordenadas.EscrituraCoordenadas;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;

/**
 * Caso de uso: registrar un hallazgo (HU-01, CU-01, secuencia 1).
 *
 * 1. Valida los datos del hallazgo y sus valores contra la ficha VIGENTE de la especie.
 *    Reúne todos los errores antes de responder (HU-01, criterio 2).
 * 2. Si algo falla, no guarda nada.
 * 3. Guarda el hallazgo y su coordenada exacta en UNA transacción: o quedan los dos, o ninguno.
 *    La coordenada va por la interfaz de solo escritura (C-3).
 */
@Service
public class ServicioRegistroHallazgos {

    private static final int LIMITE_MAXIMO = 50;

    private final RepositorioHallazgos hallazgos;
    private final RepositorioFichas fichas;
    private final EscrituraCoordenadas coordenadas;
    private final CatalogoValidadores catalogo;
    private final Clock reloj;

    public ServicioRegistroHallazgos(RepositorioHallazgos hallazgos, RepositorioFichas fichas,
                                     EscrituraCoordenadas coordenadas, CatalogoValidadores catalogo, Clock reloj) {
        this.hallazgos = hallazgos;
        this.fichas = fichas;
        this.coordenadas = coordenadas;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @Transactional
    public VistaHallazgo registrar(NuevoHallazgo datos, long investigadorId) {
        if (datos.especieId() == null) {
            throw new ReglaDeNegocioException("El hallazgo no es válido", List.of("especieId: es obligatorio"));
        }
        FichaTaxonomica ficha = fichas.fichaVigente(datos.especieId())
                .orElseThrow(() -> new NoEncontradoException("No existe la especie " + datos.especieId()));

        Map<String, Object> valores = sinNulos(datos.valores());
        List<String> errores = new ArrayList<>(
                Hallazgo.erroresDeDatos(datos.fechaObservacion(), datos.departamento(), LocalDate.now(reloj)));
        errores.addAll(ficha.validar(valores, catalogo));

        Coordenada coordenada = null;
        if (datos.coordenada() == null || datos.coordenada().latitud() == null || datos.coordenada().longitud() == null) {
            errores.add("coordenada: la ubicación exacta (latitud y longitud) es obligatoria");
        } else {
            coordenada = new Coordenada(datos.coordenada().latitud(), datos.coordenada().longitud(),
                    datos.coordenada().precisionMetros());
            errores.addAll(coordenada.errores());
        }

        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El hallazgo no es válido", errores);
        }

        Hallazgo nuevo = new Hallazgo(null, datos.especieId(), ficha.especieId(), ficha.version(), valores,
                datos.fechaObservacion(), datos.departamento(), investigadorId, null);
        long id = hallazgos.guardar(nuevo);
        coordenadas.guardar(id, coordenada);

        return consultar(id);
    }

    public VistaHallazgo consultar(long id) {
        return hallazgos.buscar(id)
                .map(VistaHallazgo::de)
                .orElseThrow(() -> new NoEncontradoException("No existe el hallazgo " + id));
    }

    public List<VistaHallazgo> listar(Long especieId, String departamento, int pagina) {
        int desplazamiento = Math.max(pagina, 0) * LIMITE_MAXIMO;
        return hallazgos.listar(especieId, departamento, LIMITE_MAXIMO, desplazamiento).stream()
                .map(VistaHallazgo::de)
                .toList();
    }

    private static Map<String, Object> sinNulos(Map<String, Object> valores) {
        Map<String, Object> limpio = new HashMap<>();
        if (valores != null) {
            valores.forEach((k, v) -> {
                if (k != null && v != null) {
                    limpio.put(k, v);
                }
            });
        }
        return limpio;
    }
}
