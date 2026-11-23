package co.edu.unbosque.biohub.aplicacion.equipos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.comun.ConflictoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.NuevoEquipo;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.Reasignacion;
import co.edu.unbosque.biohub.dominio.equipos.AsignacionEquipo;
import co.edu.unbosque.biohub.dominio.equipos.EquipoCampo;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.equipos.RepositorioEquipos;
import co.edu.unbosque.biohub.persistencia.usuarios.DirectorioPersonal;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Equipos de campo sin base de datos (S20, S21). Repositorios FAKE en memoria.
 * Usuarios: 1 investigadora, 2 directora (científica, pero no investigadora), 3 analista,
 * 4 investigador inactivo, 5 investigador.
 */
class ServicioEquiposTest {

    private static final Map<Long, Usuario> PERSONAS = Map.of(
            1L, new Usuario(1L, "ana@biohub.co", "Ana", "", Rol.INVESTIGADOR, true),
            2L, new Usuario(2L, "marta@biohub.co", "Marta", "", Rol.DIRECTOR_CIENTIFICO, true),
            3L, new Usuario(3L, "pedro@biohub.co", "Pedro", "", Rol.ANALISTA, true),
            4L, new Usuario(4L, "luis@biohub.co", "Luis", "", Rol.INVESTIGADOR, false),
            5L, new Usuario(5L, "carlos@biohub.co", "Carlos", "", Rol.INVESTIGADOR, true));

    private EquiposEnMemoria repositorio;
    private ServicioEquipos servicio;

    @BeforeEach
    void preparar() {
        repositorio = new EquiposEnMemoria();
        RepositorioUsuarios usuarios = new RepositorioUsuarios() {
            public Optional<Usuario> buscarPorCorreo(String c) { return Optional.empty(); }
            public Optional<Usuario> buscarPorId(long id) { return Optional.ofNullable(PERSONAS.get(id)); }
        };
        DirectorioPersonal directorio = () -> PERSONAS.values().stream().filter(Usuario::activo).toList();
        servicio = new ServicioEquipos(repositorio, usuarios, directorio);
    }

    @Test
    void registraElEquipoConSuResponsableYLaPrimeraAsignacion() {
        var equipo = servicio.registrar(new NuevoEquipo("dron-1", "DRON", "DJI Mavic", 1L), 2L);

        assertThat(equipo.codigo()).isEqualTo("DRON-1");
        assertThat(equipo.responsable().nombre()).isEqualTo("Ana");
        assertThat(servicio.asignaciones(equipo.id())).hasSize(1);
    }

    @Test
    void elResponsableDebeSerUnInvestigadorActivo() {   // HU-03, criterio 1
        assertThatThrownBy(() -> servicio.registrar(new NuevoEquipo("GPS-1", "GPS", null, 3L), 1L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasFieldOrPropertyWithValue("detalles", List.of("responsableId: Pedro no es investigador"));
        assertThatThrownBy(() -> servicio.registrar(new NuevoEquipo("GPS-1", "GPS", null, 2L), 1L))
                .hasFieldOrPropertyWithValue("detalles", List.of("responsableId: Marta no es investigador"));
        assertThatThrownBy(() -> servicio.registrar(new NuevoEquipo("GPS-1", "GPS", null, 4L), 2L))
                .hasFieldOrPropertyWithValue("detalles", List.of("responsableId: Luis está inactivo"));
        assertThatThrownBy(() -> servicio.registrar(new NuevoEquipo("GPS-1", "GPS", null, 99L), 2L))
                .hasFieldOrPropertyWithValue("detalles", List.of("responsableId: no existe el usuario 99"));
        assertThat(repositorio.equipos).isEmpty();
    }

    @Test
    void elCodigoNoSePuedeRepetir() {
        servicio.registrar(new NuevoEquipo("CAM-1", "CAMARA_TRAMPA", null, 1L), 2L);

        assertThatThrownBy(() -> servicio.registrar(new NuevoEquipo("cam-1", "SENSOR", null, 5L), 1L))
                .hasFieldOrPropertyWithValue("detalles", List.of("codigo: ya existe un equipo con el código CAM-1"));
    }

    @Test
    void reasignarDejaRastroEnElHistorial() {
        long id = servicio.registrar(new NuevoEquipo("SEN-1", "SENSOR", null, 1L), 2L).id();

        var equipo = servicio.reasignar(id, new Reasignacion(5L, "Ana sale de vacaciones"), 1L);

        assertThat(equipo.responsable().id()).isEqualTo(5L);
        assertThat(servicio.asignaciones(id)).extracting(VistasEquipos.VistaAsignacion::motivo)
                .containsExactly("Registro del equipo", "Ana sale de vacaciones");
    }

    @Test
    void siOtroLoReasignoAlMismoTiempoResponde409YNoCambiaNada() {
        long id = servicio.registrar(new NuevoEquipo("SEN-2", "SENSOR", null, 1L), 2L).id();
        repositorio.simularCambioConcurrente = true;

        assertThatThrownBy(() -> servicio.reasignar(id, new Reasignacion(5L, "cambio de turno"), 1L))
                .isInstanceOf(ConflictoException.class);
        assertThat(servicio.asignaciones(id)).hasSize(1);
    }

    @Test
    void losResponsablesPosiblesSonSoloLosInvestigadoresActivos() {
        assertThat(servicio.responsablesPosibles()).extracting(VistasEquipos.VistaPersona::id)
                .containsExactlyInAnyOrder(1L, 5L);
    }

    /** Imita la base: el UPDATE solo pasa si el responsable sigue siendo el anterior. */
    private static final class EquiposEnMemoria implements RepositorioEquipos {
        final Map<Long, EquipoCampo> equipos = new HashMap<>();
        final List<AsignacionEquipo> historial = new ArrayList<>();
        boolean simularCambioConcurrente;

        public long registrar(EquipoCampo e, long autor) {
            long id = equipos.size() + 1L;
            equipos.put(id, e.conId(id));
            historial.add(new AsignacionEquipo(id, e.responsableId(), "", autor, Instant.now(), "Registro del equipo"));
            return id;
        }
        public boolean existeCodigo(String c) { return equipos.values().stream().anyMatch(e -> e.codigo().equals(c)); }
        public Optional<EquipoCampo> buscar(long id) { return Optional.ofNullable(equipos.get(id)); }
        public Optional<EquipoCampo> buscarPorCodigo(String c) {
            return equipos.values().stream().filter(e -> e.codigo().equals(c)).findFirst();
        }
        public List<EquipoCampo> listar(Long r) {
            return equipos.values().stream().filter(e -> r == null || e.responsableId().equals(r)).toList();
        }
        public boolean reasignar(long id, long anterior, long nuevo, long autor, String motivo) {
            EquipoCampo e = equipos.get(id);
            if (simularCambioConcurrente || e.responsableId() != anterior) {
                return false;
            }
            equipos.put(id, new EquipoCampo(id, e.codigo(), e.tipo(), e.descripcion(), nuevo));
            historial.add(new AsignacionEquipo(id, nuevo, "", autor, Instant.now(), motivo));
            return true;
        }
        public List<AsignacionEquipo> asignaciones(long id) {
            return historial.stream().filter(a -> a.equipoId() == id).toList();
        }
    }
}
