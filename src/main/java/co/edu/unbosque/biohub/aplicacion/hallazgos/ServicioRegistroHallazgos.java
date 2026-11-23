package co.edu.unbosque.biohub.aplicacion.hallazgos;

import java.time.Clock;
import java.time.LocalDate;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.InvalidadorCachePublica;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.NuevoHallazgo;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.VistaHallazgo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;
import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;
import co.edu.unbosque.biohub.dominio.hallazgos.Departamentos;
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

    /** 100 000 páginas de 50 = 5 millones de hallazgos: de sobra, y sin desbordar el OFFSET. */
    static final int PAGINA_MAXIMA = 100_000;
    private static final int LIMITE_MAXIMO = 50;

    private final RepositorioHallazgos hallazgos;
    private final RepositorioFichas fichas;
    private final EscrituraCoordenadas coordenadas;
    private final CatalogoValidadores catalogo;
    private final Clock reloj;
    private final InvalidadorCachePublica cachePublica;

    public ServicioRegistroHallazgos(RepositorioHallazgos hallazgos, RepositorioFichas fichas,
                                     EscrituraCoordenadas coordenadas, CatalogoValidadores catalogo, Clock reloj,
                                     InvalidadorCachePublica cachePublica) {
        this.hallazgos = hallazgos;
        this.fichas = fichas;
        this.coordenadas = coordenadas;
        this.catalogo = catalogo;
        this.reloj = reloj;
        this.cachePublica = cachePublica;
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
        // Secuencia 1, paso 18: la ficha pública cuenta hallazgos por departamento
        cachePublica.hallazgosCambiaron(datos.especieId());

        return consultar(id);
    }

    public VistaHallazgo consultar(long id) {
        Hallazgo h = hallazgos.buscar(id)
                .orElseThrow(() -> new NoEncontradoException("No existe el hallazgo " + id));
        return VistaHallazgo.de(h, fichaDe(h, new HashMap<>()));
    }

    public List<VistaHallazgo> listar(Long especieId, String departamento, int pagina) {
        if (pagina < 0 || pagina > PAGINA_MAXIMA) {
            // Sin este tope, pagina * 50 se desborda y PostgreSQL recibe un OFFSET negativo (500)
            throw new ReglaDeNegocioException("La página no es válida",
                    List.of("pagina: debe estar entre 0 y " + PAGINA_MAXIMA));
        }
        int desplazamiento = pagina * LIMITE_MAXIMO;
        // La ficha se pide una vez por especie y versión, no una vez por hallazgo
        Map<String, FichaTaxonomica> fichasUsadas = new HashMap<>();
        return hallazgos.listar(especieId, departamento, LIMITE_MAXIMO, desplazamiento).stream()
                .map(h -> VistaHallazgo.de(h, fichaDe(h, fichasUsadas)))
                .toList();
    }

    /** La ficha en la versión con que se registró el hallazgo: decide qué valores son de ubicación. */
    private FichaTaxonomica fichaDe(Hallazgo h, Map<String, FichaTaxonomica> yaLeidas) {
        return yaLeidas.computeIfAbsent(h.fichaEspecieId() + "/" + h.versionFicha(),
                k -> fichas.fichaEnVersion(h.fichaEspecieId(), h.versionFicha())
                        .orElseThrow(() -> new IllegalStateException("Falta la ficha del hallazgo " + h.id())));
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

    /** Los 33 departamentos válidos, en orden alfabético español (para la lista de selección). */
    public List<String> departamentos() {
        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        return Departamentos.TODOS.stream().sorted(orden).toList();
    }
}
