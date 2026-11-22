package co.edu.unbosque.biohub.aplicacion.consultapublica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.VistasConsultaPublica.VistaFichaPublica;
import co.edu.unbosque.biohub.dominio.consultapublica.PresenciaEnDepartamento;
import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;
import co.edu.unbosque.biohub.persistencia.consultapublica.RepositorioConsultaPublica;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.soporte.AlmacenCacheEnMemoria;
import co.edu.unbosque.biohub.soporte.RelojAjustable;

/**
 * PR-17 (RNF-03): el Lector público funciona con AlmacenCacheEnMemoria (fake), sin Redis.
 * El repositorio cuenta cuántas veces se le consulta, para saber si la respuesta salió de la caché.
 */
class LectorPublicoTest {

    private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("America/Bogota"));
    private final AlmacenCacheEnMemoria cache = new AlmacenCacheEnMemoria(reloj);
    private int consultasALaBase;
    private LectorPublico lector;

    @BeforeEach
    void preparar() {
        FichaTaxonomica ficha = FichaTaxonomica.inicial(2L, List.of(
                new DefinicionAtributo("ancho_huella", TipoDato.NUMERO, true, true, List.of(), "cm")));

        RepositorioFichas fichas = new RepositorioFichas() {
            public List<Especie> especies() {
                consultasALaBase++;
                return List.of(new Especie(2L, "Tremarctos ornatus", "Oso andino", true),
                        new Especie(3L, "Turdus fuscater", "Mirla grande", false));
            }
            public Optional<Especie> especie(long id) {
                consultasALaBase++;
                return id == 2L ? Optional.of(new Especie(2L, "Tremarctos ornatus", "Oso andino", true)) : Optional.empty();
            }
            public long crearEspecie(String a, String b, boolean c) { return 0; }
            public boolean existeNombreCientifico(String n) { return false; }
            public Optional<FichaTaxonomica> fichaVigente(long id) { return id == 2L ? Optional.of(ficha) : Optional.empty(); }
            public Optional<FichaTaxonomica> fichaEnVersion(long id, int v) { return Optional.empty(); }
            public void guardarVersion(FichaTaxonomica f, long a) { }
        };
        RepositorioConsultaPublica consultas = new RepositorioConsultaPublica() {
            public Map<Long, Long> hallazgosPorEspecie() { return Map.of(2L, 5L); }
            public List<PresenciaEnDepartamento> presencia(long id) {
                return List.of(new PresenciaEnDepartamento("Cundinamarca", 3, LocalDate.of(2026, 11, 1)),
                        new PresenciaEnDepartamento("Boyacá", 2, LocalDate.of(2026, 10, 20)));
            }
        };
        lector = new LectorPublico(fichas, consultas, cache, new ObjectMapper().findAndRegisterModules(), 3);
    }

    @Test
    void laSegundaConsultaSaleDeLaCacheSinIrALaBase() {
        VistaFichaPublica primera = lector.ficha(2L);
        VistaFichaPublica segunda = lector.ficha(2L);

        assertThat(consultasALaBase).isEqualTo(1);
        assertThat(segunda).isEqualTo(primera);
    }

    @Test
    void cuandoVenceLaVidaVuelveALaBase() {
        lector.ficha(2L);

        reloj.adelantar(Duration.ofMillis(2999));
        lector.ficha(2L);
        assertThat(consultasALaBase).as("a los 2,999 s sigue en caché").isEqualTo(1);

        reloj.adelantar(Duration.ofMillis(1));
        lector.ficha(2L);
        assertThat(consultasALaBase).as("a los 3 s vence").isEqualTo(2);
    }

    @Test
    void laFichaMuestraSoloDepartamentosYTotales() {
        VistaFichaPublica ficha = lector.ficha(2L);

        assertThat(ficha.totalHallazgos()).isEqualTo(5);
        assertThat(ficha.departamentos()).extracting(d -> d.departamento()).containsExactly("Cundinamarca", "Boyacá");
        assertThat(ficha.atributos()).extracting(a -> a.nombre()).containsExactly("ancho_huella");
    }

    @Test
    void unaEspecieQueNoExisteNoSeGuardaEnLaCache() {
        assertThatThrownBy(() -> lector.ficha(99L)).isInstanceOf(NoEncontradoException.class);

        assertThat(cache.contiene(LectorPublico.claveFicha(99L))).isFalse();
    }

    @Test
    void unValorDanadoEnLaCacheSeIgnoraYSeVuelveACargar() {
        cache.guardar(LectorPublico.claveFicha(2L), "{esto no es json", Duration.ofMinutes(1));

        VistaFichaPublica ficha = lector.ficha(2L);

        assertThat(ficha.nombreComun()).isEqualTo("Oso andino");
        assertThat(consultasALaBase).isEqualTo(1);
    }

    @Test
    void laBusquedaIgnoraMayusculasYTildesYUsaLaCache() {
        assertThat(lector.buscar("OSO")).extracting(e -> e.id()).containsExactly(2L);
        assertThat(lector.buscar("turdus")).extracting(e -> e.id()).containsExactly(3L);
        assertThat(lector.buscar("mírla")).extracting(e -> e.id()).containsExactly(3L);
        assertThat(lector.buscar(null)).hasSize(2);

        assertThat(consultasALaBase).as("las 4 búsquedas usan el mismo listado en caché").isEqualTo(1);
    }

    @Test
    void elListadoTraeElTotalDeHallazgosDeCadaEspecie() {
        assertThat(lector.buscar("")).extracting(e -> e.totalHallazgos()).containsExactly(5L, 0L);
    }
}
