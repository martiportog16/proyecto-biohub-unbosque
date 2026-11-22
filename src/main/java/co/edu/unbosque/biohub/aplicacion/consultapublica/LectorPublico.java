package co.edu.unbosque.biohub.aplicacion.consultapublica;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaAtributoPublico;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaDepartamento;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaEspeciePublica;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaFichaPublica;
import co.edu.unbosque.biohub.dominio.consultapublica.PresenciaEnDepartamento;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.persistencia.cache.AlmacenCache;
import co.edu.unbosque.biohub.persistencia.consultapublica.RepositorioConsultaPublica;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;

/**
 * Lector público (HU-04, secuencia 2): primero la caché, y solo si no está, la base.
 *
 *   1. Busca la respuesta en la caché (Redis, por medio de AlmacenCache).
 *   2. Si no está: la arma desde la réplica pública y la guarda por unos segundos.
 *
 * Con 300 peticiones/s a una misma ficha (P-12), casi todas salen de la caché y la
 * réplica recibe una consulta cada pocos segundos (RNF-02, RNF-03).
 *
 * La vida de cada entrada es corta (3 s por defecto) a propósito: aunque nadie borre
 * la caché, un dato nuevo aparece en el portal en ≤ 3 s + el retraso de la réplica,
 * dentro de los 5 s de P-14.
 */
@Service
public class LectorPublico {

    public static final String CLAVE_LISTADO = "publico:especies";

    private final RepositorioFichas fichas;
    private final RepositorioConsultaPublica consultas;
    private final AlmacenCache cache;
    private final ObjectMapper json;
    private final Duration vida;

    public LectorPublico(RepositorioFichas fichas, RepositorioConsultaPublica consultas, AlmacenCache cache,
                         ObjectMapper json, @Value("${biohub.cache.vida-segundos:3}") long vidaSegundos) {
        this.fichas = fichas;
        this.consultas = consultas;
        this.cache = cache;
        this.json = json;
        this.vida = Duration.ofSeconds(vidaSegundos);
    }

    public static String claveFicha(long especieId) {
        return "publico:ficha:" + especieId;
    }

    /** Especies cuyo nombre científico o común contiene el texto (sin importar mayúsculas ni tildes). */
    public List<VistaEspeciePublica> buscar(String texto) {
        List<VistaEspeciePublica> todas = desdeCache(CLAVE_LISTADO,
                json.getTypeFactory().constructCollectionType(List.class, VistaEspeciePublica.class),
                this::cargarListado);
        if (texto == null || texto.isBlank()) {
            return todas;
        }
        String buscado = normalizar(texto);
        return todas.stream()
                .filter(e -> normalizar(e.nombreCientifico()).contains(buscado)
                        || normalizar(e.nombreComun()).contains(buscado))
                .toList();
    }

    public VistaFichaPublica ficha(long especieId) {
        return desdeCache(claveFicha(especieId), json.constructType(VistaFichaPublica.class),
                () -> cargarFicha(especieId));
    }

    private <T> T desdeCache(String clave, JavaType tipo, Supplier<T> cargar) {
        Optional<String> guardado = cache.leer(clave);
        if (guardado.isPresent()) {
            try {
                return json.readValue(guardado.get(), tipo);
            } catch (JsonProcessingException e) {
                // Un valor dañado no tumba el portal: se vuelve a cargar desde la base.
            }
        }
        T valor = cargar.get();
        try {
            cache.guardar(clave, json.writeValueAsString(valor), vida);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return valor;
    }

    private List<VistaEspeciePublica> cargarListado() {
        Map<Long, Long> totales = consultas.hallazgosPorEspecie();
        return fichas.especies().stream()
                .map(e -> new VistaEspeciePublica(e.id(), e.nombreCientifico(), e.nombreComun(), e.enPeligro(),
                        totales.getOrDefault(e.id(), 0L)))
                .toList();
    }

    private VistaFichaPublica cargarFicha(long especieId) {
        Especie especie = fichas.especie(especieId)
                .orElseThrow(() -> new NoEncontradoException("No existe la especie " + especieId));
        FichaTaxonomica ficha = fichas.fichaVigente(especieId)
                .orElseThrow(() -> new NoEncontradoException("La especie " + especieId + " no tiene ficha"));
        List<PresenciaEnDepartamento> presencia = consultas.presencia(especieId);

        return new VistaFichaPublica(especie.id(), especie.nombreCientifico(), especie.nombreComun(),
                especie.enPeligro(), ficha.version(),
                ficha.activos().stream().map(a -> new VistaAtributoPublico(a.nombre(), a.tipo().name(), a.unidad())).toList(),
                presencia.stream().mapToLong(PresenciaEnDepartamento::hallazgos).sum(),
                presencia.stream().map(p -> new VistaDepartamento(p.departamento(), p.hallazgos(), p.ultimaObservacion())).toList());
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip();
    }
}
