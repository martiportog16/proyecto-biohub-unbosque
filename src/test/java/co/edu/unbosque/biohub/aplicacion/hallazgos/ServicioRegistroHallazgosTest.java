package co.edu.unbosque.biohub.aplicacion.hallazgos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.NuevaCoordenada;
import co.edu.unbosque.biohub.aplicacion.hallazgos.VistasHallazgos.NuevoHallazgo;
import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;
import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;
import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.persistencia.coordenadas.EscrituraCoordenadas;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;

/**
 * Registro de hallazgos sin base de datos (S20 y S21):
 * - RELOJ FIJO (stub): "hoy" es siempre 15 de noviembre de 2026.
 * - Repositorios FAKE en memoria.
 * - Escritura de coordenadas SPY: anota qué se le pidió guardar.
 */
class ServicioRegistroHallazgosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final List<Hallazgo> guardados = new ArrayList<>();
    private final Map<Long, Coordenada> coordenadasGuardadas = new HashMap<>();
    private ServicioRegistroHallazgos servicio;

    @BeforeEach
    void preparar() {
        FichaTaxonomica ficha = FichaTaxonomica.inicial(7L, List.of(
                new DefinicionAtributo("conteo", TipoDato.NUMERO, true, true, List.of(), null)));

        RepositorioHallazgos hallazgosFake = new RepositorioHallazgos() {
            public long guardar(Hallazgo h) {
                long id = guardados.size() + 1;
                guardados.add(new Hallazgo(id, h.especieId(), h.fichaEspecieId(), h.versionFicha(), h.valores(),
                        h.fechaObservacion(), h.departamento(), h.investigadorId(), Instant.EPOCH));
                return id;
            }
            public Optional<Hallazgo> buscar(long id) { return Optional.of(guardados.get((int) id - 1)); }
            public List<Hallazgo> listar(Long e, String d, int l, int o) { return guardados; }
        };
        RepositorioFichas fichasFake = new RepositorioFichas() {
            public List<Especie> especies() { return List.of(); }
            public Optional<Especie> especie(long id) { return Optional.empty(); }
            public long crearEspecie(String a, String b, boolean c) { return 0; }
            public boolean existeNombreCientifico(String n) { return false; }
            public Optional<FichaTaxonomica> fichaVigente(long id) { return id == 7L ? Optional.of(ficha) : Optional.empty(); }
            public Optional<FichaTaxonomica> fichaEnVersion(long id, int v) { return Optional.empty(); }
            public void guardarVersion(FichaTaxonomica f, long a) { }
        };
        EscrituraCoordenadas spy = coordenadasGuardadas::put;
        Clock relojFijo = Clock.fixed(HOY.atStartOfDay(BOGOTA).toInstant(), BOGOTA);

        servicio = new ServicioRegistroHallazgos(hallazgosFake, fichasFake, spy,
                CatalogoValidadores.estandar(), relojFijo);
    }

    private NuevoHallazgo hallazgo(LocalDate fecha, String departamento, Map<String, Object> valores, NuevaCoordenada c) {
        return new NuevoHallazgo(7L, fecha, departamento, valores, c);
    }

    @Test
    void unHallazgoValidoSeGuardaConSuCoordenadaYLaVersionDeLaFicha() {
        var vista = servicio.registrar(hallazgo(HOY, "Cundinamarca", Map.of("conteo", 3),
                new NuevaCoordenada(4.6097, -74.0817, 10)), 1L);

        assertThat(vista.versionFicha()).isEqualTo(1);
        assertThat(guardados).hasSize(1);
        assertThat(coordenadasGuardadas).containsKey(vista.id());
        assertThat(coordenadasGuardadas.get(vista.id()).latitud()).isEqualTo(4.6097);
    }

    @Test
    void laFechaDeHoyEsValidaPeroLaDeMananaNo() {
        servicio.registrar(hallazgo(HOY, "Meta", Map.of("conteo", 1), new NuevaCoordenada(4.0, -73.0, null)), 1L);

        assertThatThrownBy(() -> servicio.registrar(hallazgo(HOY.plusDays(1), "Meta", Map.of("conteo", 1),
                new NuevaCoordenada(4.0, -73.0, null)), 1L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles())
                        .containsExactly("fechaObservacion: no puede ser una fecha futura"));
    }

    @Test
    void reuneTodosLosErroresYNoGuardaNada() {
        NuevoHallazgo malo = hallazgo(HOY.plusDays(3), "Narnia", Map.of("conteo", "muchos"),
                new NuevaCoordenada(95.0, -74.0, 0));

        assertThatThrownBy(() -> servicio.registrar(malo, 1L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles()).hasSize(5));

        assertThat(guardados).isEmpty();
        assertThat(coordenadasGuardadas).isEmpty();
    }

    @Test
    void sinCoordenadaNoSeRegistra() {
        assertThatThrownBy(() -> servicio.registrar(hallazgo(HOY, "Meta", Map.of("conteo", 1), null), 1L))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(guardados).isEmpty();
    }
}
