package co.edu.unbosque.biohub.aplicacion.autorizacion;

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

import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaCoordenada;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaMapa;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.dominio.coordenadas.AccesoCoordenadas;
import co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.coordenadas.LecturaCoordenadas;
import co.edu.unbosque.biohub.persistencia.coordenadas.RegistroAccesos;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * El componente de autorización sin base de datos (S20: dobles de prueba).
 *
 * - La lectura de coordenadas es un SPY: anota cuántas veces la llamaron. Así se
 *   comprueba que, cuando se niega el acceso, las coordenadas NI SIQUIERA se leen.
 * - El registro de accesos es un FAKE en memoria que se puede "dañar".
 * - El reloj está FIJO.
 *
 * Especie 1 = en peligro, especie 3 = fuera de peligro.
 * Hallazgo 10 es de la especie 1; hallazgo 30 es de la especie 3.
 * Usuario 5 = alto rango, 6 = investigador, 7 = analista.
 */
class AutorizacionCoordenadasTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final long ALTO_RANGO = 5, INVESTIGADOR = 6, ANALISTA = 7;

    private final Map<Long, Usuario> usuariosEnBase = new HashMap<>();
    private final List<AccesoCoordenadas> registrados = new ArrayList<>();
    private int lecturasDeCoordenadas;
    private boolean registroDanado;
    private AutorizacionCoordenadas autorizacion;

    @BeforeEach
    void preparar() {
        usuariosEnBase.put(ALTO_RANGO, new Usuario(ALTO_RANGO, "alto@biohub.co", "Alto", "", Rol.ALTO_RANGO, true));
        usuariosEnBase.put(INVESTIGADOR, new Usuario(INVESTIGADOR, "inv@biohub.co", "Inv", "", Rol.INVESTIGADOR, true));
        usuariosEnBase.put(ANALISTA, new Usuario(ANALISTA, "ana@biohub.co", "Ana", "", Rol.ANALISTA, true));

        LecturaCoordenadas lecturaEspia = new LecturaCoordenadas() {
            public Optional<UbicacionExacta> deHallazgo(long id) {
                lecturasDeCoordenadas++;
                return Optional.of(ubicacion(id));
            }
            public List<UbicacionExacta> deEspecie(long especieId, int limite) {
                lecturasDeCoordenadas++;
                return List.of(ubicacion(especieId * 10), ubicacion(especieId * 10 + 1));
            }
        };
        RegistroAccesos registro = new RegistroAccesos() {
            public void registrar(AccesoCoordenadas a) {
                if (registroDanado) {
                    throw new IllegalStateException("No se pudo guardar el registro de accesos");
                }
                // como la base: al guardar se le asigna un id
                registrados.add(new AccesoCoordenadas((long) registrados.size() + 1, a.usuarioId(), a.rol(),
                        a.especieId(), a.hallazgoId(), a.especieEnPeligro(), a.permitido(), a.motivo(), a.ocurridoEn()));
            }
            public List<AccesoCoordenadas> recientes(boolean soloNegados, int limite) {
                return registrados.stream().filter(a -> !soloNegados || !a.permitido()).limit(limite).toList();
            }
        };
        RepositorioHallazgos hallazgos = new RepositorioHallazgos() {
            public long guardar(Hallazgo h) { return 0; }
            public Optional<Hallazgo> buscar(long id) {
                if (id != 10 && id != 30) {
                    return Optional.empty();
                }
                long especie = id == 10 ? 1 : 3;
                return Optional.of(new Hallazgo(id, especie, especie, 1, Map.of(), LocalDate.of(2026, 11, 1),
                        "Meta", INVESTIGADOR, AHORA));
            }
            public List<Hallazgo> listar(Long e, String d, int l, int o) { return List.of(); }
        };
        RepositorioFichas fichas = new RepositorioFichas() {
            public List<Especie> especies() { return List.of(); }
            public Optional<Especie> especie(long id) {
                if (id == 1) return Optional.of(new Especie(1L, "Ateles hybridus", "Mono araña", true));
                if (id == 3) return Optional.of(new Especie(3L, "Turdus fuscater", "Mirla", false));
                return Optional.empty();
            }
            public long crearEspecie(String a, String b, boolean c) { return 0; }
            public boolean existeNombreCientifico(String n) { return false; }
            public Optional<FichaTaxonomica> fichaVigente(long id) { return Optional.empty(); }
            public Optional<FichaTaxonomica> fichaEnVersion(long id, int v) { return Optional.empty(); }
            public void guardarVersion(FichaTaxonomica f, long a) { }
        };
        RepositorioUsuarios usuarios = new RepositorioUsuarios() {
            public Optional<Usuario> buscarPorCorreo(String c) { return Optional.empty(); }
            public Optional<Usuario> buscarPorId(long id) { return Optional.ofNullable(usuariosEnBase.get(id)); }
        };

        autorizacion = new AutorizacionCoordenadas(lecturaEspia, registro, hallazgos, fichas, usuarios,
                Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    private static UbicacionExacta ubicacion(long hallazgoId) {
        return new UbicacionExacta(hallazgoId, "Meta", LocalDate.of(2026, 11, 1), new Coordenada(4.123456, -73.654321, 15));
    }

    @Test
    void altoRangoVeLaCoordenadaDeUnaEspecieEnPeligroYQuedaRegistrado() {
        VistaCoordenada vista = autorizacion.coordenadaDe(10, ALTO_RANGO);

        assertThat(vista.latitud()).isEqualTo(4.123456);
        assertThat(vista.longitud()).isEqualTo(-73.654321);
        assertThat(registrados).singleElement().satisfies(a -> {
            assertThat(a.usuarioId()).isEqualTo(ALTO_RANGO);
            assertThat(a.rol()).isEqualTo("ALTO_RANGO");
            assertThat(a.especieId()).isEqualTo(1);
            assertThat(a.hallazgoId()).isEqualTo(10);
            assertThat(a.especieEnPeligro()).isTrue();
            assertThat(a.permitido()).isTrue();
            assertThat(a.ocurridoEn()).isEqualTo(AHORA);
        });
    }

    @Test
    void alInvestigadorSeLeNiegaLaEspecieEnPeligroSinLeerLasCoordenadasYQuedaRegistrado() {
        assertThatThrownBy(() -> autorizacion.coordenadaDe(10, INVESTIGADOR))
                .isInstanceOf(AccesoCoordenadasDenegadoException.class)
                .hasMessageContaining("alto rango");

        assertThat(lecturasDeCoordenadas).as("negado = las coordenadas ni se leen").isZero();
        assertThat(registrados).singleElement().satisfies(a -> {
            assertThat(a.permitido()).isFalse();
            assertThat(a.motivo()).isEqualTo("ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO");
        });
    }

    @Test
    void elInvestigadorSiVeUnaEspecieFueraDePeligro() {
        assertThat(autorizacion.coordenadaDe(30, INVESTIGADOR).hallazgoId()).isEqualTo(30);
        assertThat(registrados).singleElement().satisfies(a -> assertThat(a.permitido()).isTrue());
    }

    @Test
    void unRolNoCientificoNoVeNiLasEspeciesFueraDePeligro() {
        assertThatThrownBy(() -> autorizacion.coordenadaDe(30, ANALISTA))
                .isInstanceOf(AccesoCoordenadasDenegadoException.class);

        assertThat(lecturasDeCoordenadas).isZero();
        assertThat(registrados).singleElement().satisfies(a -> assertThat(a.motivo()).isEqualTo("ROL_NO_CIENTIFICO"));
    }

    @Test
    void elRolSeTomaDeLaBaseYNoDelToken() {
        // Al usuario 5 le quitaron el alto rango DESPUÉS de iniciar sesión.
        // Su token viejo todavía diría ALTO_RANGO, pero la autorización mira la base.
        usuariosEnBase.put(ALTO_RANGO, new Usuario(ALTO_RANGO, "alto@biohub.co", "Alto", "", Rol.INVESTIGADOR, true));

        assertThatThrownBy(() -> autorizacion.coordenadaDe(10, ALTO_RANGO))
                .isInstanceOf(AccesoCoordenadasDenegadoException.class);
        assertThat(registrados).singleElement().satisfies(a -> assertThat(a.rol()).isEqualTo("INVESTIGADOR"));
    }

    @Test
    void unUsuarioDesactivadoYaNoVeCoordenadas() {
        usuariosEnBase.put(ALTO_RANGO, new Usuario(ALTO_RANGO, "alto@biohub.co", "Alto", "", Rol.ALTO_RANGO, false));

        assertThatThrownBy(() -> autorizacion.coordenadaDe(10, ALTO_RANGO))
                .isInstanceOf(AccesoCoordenadasDenegadoException.class);
        assertThat(lecturasDeCoordenadas).isZero();
        assertThat(registrados).singleElement().satisfies(a -> assertThat(a.motivo()).isEqualTo("USUARIO_INACTIVO"));
    }

    @Test
    void siNoSePuedeRegistrarElAccesoNoSeEntreganCoordenadas() {
        // "Falla cerrado": sin evidencia no hay acceso, ni para alto rango.
        registroDanado = true;

        assertThatThrownBy(() -> autorizacion.coordenadaDe(10, ALTO_RANGO))
                .isInstanceOf(IllegalStateException.class);
        assertThat(lecturasDeCoordenadas).isZero();
    }

    @Test
    void elMapaDeUnaEspecieSigueLaMismaReglaYSeRegistraSinHallazgo() {
        VistaMapa mapa = autorizacion.mapaDeEspecie(1, ALTO_RANGO);
        assertThat(mapa.total()).isEqualTo(2);
        assertThat(mapa.enPeligro()).isTrue();

        assertThatThrownBy(() -> autorizacion.mapaDeEspecie(1, INVESTIGADOR))
                .isInstanceOf(AccesoCoordenadasDenegadoException.class);

        assertThat(registrados).extracting(AccesoCoordenadas::permitido).containsExactly(true, false);
        assertThat(registrados).allSatisfy(a -> assertThat(a.hallazgoId()).isNull());
        assertThat(lecturasDeCoordenadas).isEqualTo(1);
    }

    @Test
    void unHallazgoOUnaEspecieQueNoExistenResponden404SinRegistro() {
        assertThatThrownBy(() -> autorizacion.coordenadaDe(999, ALTO_RANGO)).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> autorizacion.mapaDeEspecie(999, ALTO_RANGO)).isInstanceOf(NoEncontradoException.class);

        assertThat(registrados).isEmpty();
    }

    @Test
    void elAuditorPuedeFiltrarSoloLosIntentosNegados() {
        autorizacion.coordenadaDe(10, ALTO_RANGO);
        try {
            autorizacion.coordenadaDe(10, INVESTIGADOR);
        } catch (AccesoCoordenadasDenegadoException esperado) {
            // el intento negado queda en el registro
        }

        assertThat(autorizacion.accesosRecientes(false, 50)).hasSize(2);
        assertThat(autorizacion.accesosRecientes(true, 50)).singleElement()
                .satisfies(a -> assertThat(a.usuarioId()).isEqualTo(INVESTIGADOR));
    }
}
