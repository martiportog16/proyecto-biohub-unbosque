package co.edu.unbosque.biohub.aplicacion.historial;


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

import co.edu.unbosque.biohub.aplicacion.comun.ConflictoException;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.NuevaReclasificacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaReclasificacion;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.dominio.historial.Reclasificacion;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;
import co.edu.unbosque.biohub.persistencia.historial.RepositorioHistorial;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;
import co.edu.unbosque.biohub.aplicacion.consultapublica.InvalidadorCachePublica;
import co.edu.unbosque.biohub.aplicacion.consultapublica.LectorPublico;
import co.edu.unbosque.biohub.soporte.AlmacenCacheEnMemoria;

/**
 * Reclasificar sin base de datos (S20, S21).
 * - Reloj FIJO (stub): la fecha del cambio es siempre la misma.
 * - Repositorios FAKE en memoria: el historial imita el control optimista de la base.
 * Hallazgo 1 está en la especie 10. Existen las especies 10 y 20. El director es el usuario 3.
 */
class ServicioReclasificacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T15:00:00Z");
    private static final long DIRECTOR = 3L;

    private final Map<Long, Long> especieDelHallazgo = new HashMap<>();
    private final List<Reclasificacion> guardadas = new ArrayList<>();
    private boolean simularConflicto;
    private final AlmacenCacheEnMemoria cache = new AlmacenCacheEnMemoria(java.time.Clock.systemUTC());
    private ServicioReclasificacion servicio;

    @BeforeEach
    void preparar() {
        especieDelHallazgo.put(1L, 10L);

        RepositorioHallazgos hallazgos = new RepositorioHallazgos() {
            public long guardar(Hallazgo h) { return 0; }
            public Optional<Hallazgo> buscar(long id) {
                return Optional.ofNullable(especieDelHallazgo.get(id)).map(especie -> new Hallazgo(id, especie, 10L, 1,
                        Map.of(), LocalDate.of(2026, 10, 1), "Meta", 5L, Instant.parse("2026-10-01T12:00:00Z")));
            }
            public List<Hallazgo> listar(Long e, String d, int l, int o) { return List.of(); }
        };
        RepositorioHistorial historial = new RepositorioHistorial() {
            public Optional<Long> registrar(Reclasificacion r) {
                if (simularConflicto || !especieDelHallazgo.get(r.hallazgoId()).equals(r.especieAnteriorId())) {
                    return Optional.empty();
                }
                especieDelHallazgo.put(r.hallazgoId(), r.especieNuevaId());
                guardadas.add(r);
                return Optional.of((long) guardadas.size());
            }
            public List<Reclasificacion> deHallazgo(long id) {
                return guardadas.stream().filter(r -> r.hallazgoId() == id).toList();
            }
        };
        RepositorioFichas fichas = new RepositorioFichas() {
            public List<Especie> especies() {
                return List.of(new Especie(10L, "Turdus fuscater", "Mirla", false),
                        new Especie(20L, "Turdus serranus", "Mirla negra", false));
            }
            public Optional<Especie> especie(long id) { return especies().stream().filter(e -> e.id() == id).findFirst(); }
            public long crearEspecie(String a, String b, boolean c) { return 0; }
            public boolean existeNombreCientifico(String n) { return false; }
            public Optional<FichaTaxonomica> fichaVigente(long id) { return Optional.empty(); }
            public Optional<FichaTaxonomica> fichaEnVersion(long id, int v) { return Optional.empty(); }
            public void guardarVersion(FichaTaxonomica f, long a) { }
        };
        RepositorioUsuarios usuarios = new RepositorioUsuarios() {
            public Optional<Usuario> buscarPorCorreo(String c) { return Optional.empty(); }
            public Optional<Usuario> buscarPorId(long id) {
                return Optional.of(new Usuario(id, "director@biohub.co", "Directora Demo", "", Rol.DIRECTOR_CIENTIFICO, true));
            }
        };
        Clock relojFijo = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

        servicio = new ServicioReclasificacion(hallazgos, historial, fichas,
                new NombresHistorial(fichas, usuarios), relojFijo, new InvalidadorCachePublica(cache));
    }

    @Test
    void guardaLosCincoDatosYCambiaLaEspecieActual() {
        VistaReclasificacion vista = servicio.reclasificar(1L,
                new NuevaReclasificacion(20L, "  Análisis de ADN mitocondrial  "), DIRECTOR);

        assertThat(guardadas).singleElement().satisfies(r -> {
            assertThat(r.autorId()).isEqualTo(DIRECTOR);
            assertThat(r.realizadaEn()).isEqualTo(AHORA);
            assertThat(r.motivo()).isEqualTo("Análisis de ADN mitocondrial");
            assertThat(r.especieAnteriorId()).isEqualTo(10L);
            assertThat(r.especieNuevaId()).isEqualTo(20L);
        });
        assertThat(especieDelHallazgo.get(1L)).isEqualTo(20L);
        assertThat(vista.especieAnterior().nombreCientifico()).isEqualTo("Turdus fuscater");
        assertThat(vista.especieNueva().nombreCientifico()).isEqualTo("Turdus serranus");
        assertThat(vista.autor().nombre()).isEqualTo("Directora Demo");
    }

    @Test
    void borraLaFichaPublicaDeLaEspecieAnteriorYDeLaNueva() {   // T-07, secuencia 3 pasos 20 y 21
        for (long especie : new long[] {10L, 20L, 30L}) {
            cache.guardar(LectorPublico.claveFicha(especie), "ficha", java.time.Duration.ofMinutes(1));
        }

        servicio.reclasificar(1L, new NuevaReclasificacion(20L, "Análisis de ADN mitocondrial"), DIRECTOR);

        assertThat(cache.contiene(LectorPublico.claveFicha(10L))).isFalse();
        assertThat(cache.contiene(LectorPublico.claveFicha(20L))).isFalse();
        assertThat(cache.contiene(LectorPublico.claveFicha(30L))).as("otras especies no se tocan").isTrue();
    }

    @Test
    void sinMotivoSeRechazaYNoSeGuardaNada() {
        assertThatThrownBy(() -> servicio.reclasificar(1L, new NuevaReclasificacion(20L, ""), DIRECTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .extracting(e -> ((ReglaDeNegocioException) e).getDetalles())
                .isEqualTo(List.of("motivo: es obligatorio"));

        assertThat(guardadas).isEmpty();
        assertThat(especieDelHallazgo.get(1L)).isEqualTo(10L);
    }

    @Test
    void unaEspecieQueNoExisteSeRechaza() {
        assertThatThrownBy(() -> servicio.reclasificar(1L, new NuevaReclasificacion(99L, "Nueva evidencia genética"), DIRECTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .extracting(e -> ((ReglaDeNegocioException) e).getDetalles())
                .isEqualTo(List.of("especieNuevaId: no existe la especie 99"));
    }

    @Test
    void unHallazgoQueNoExisteResponde404() {
        assertThatThrownBy(() -> servicio.reclasificar(77L, new NuevaReclasificacion(20L, "Nueva evidencia genética"), DIRECTOR))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void siOtroDirectorReclasificoAlMismoTiempoEsUnConflicto() {
        simularConflicto = true;

        assertThatThrownBy(() -> servicio.reclasificar(1L, new NuevaReclasificacion(20L, "Nueva evidencia genética"), DIRECTOR))
                .isInstanceOf(ConflictoException.class);
        assertThat(guardadas).isEmpty();
    }

    @Test
    void dosReclasificacionesSeguidasQuedanEncadenadas() {
        servicio.reclasificar(1L, new NuevaReclasificacion(20L, "Nueva evidencia genética"), DIRECTOR);
        servicio.reclasificar(1L, new NuevaReclasificacion(10L, "Se revirtió tras revisión de pares"), DIRECTOR);

        assertThat(guardadas).extracting(Reclasificacion::especieAnteriorId).containsExactly(10L, 20L);
        assertThat(guardadas).extracting(Reclasificacion::especieNuevaId).containsExactly(20L, 10L);
    }
}
