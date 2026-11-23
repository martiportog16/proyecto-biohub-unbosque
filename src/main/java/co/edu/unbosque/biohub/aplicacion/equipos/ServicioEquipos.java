package co.edu.unbosque.biohub.aplicacion.equipos;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.ConflictoException;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.NuevoEquipo;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.Reasignacion;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaAsignacion;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaEquipo;
import co.edu.unbosque.biohub.aplicacion.equipos.VistasEquipos.VistaPersona;
import co.edu.unbosque.biohub.dominio.equipos.EquipoCampo;
import co.edu.unbosque.biohub.dominio.equipos.ReglaDeEquipoException;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.equipos.RepositorioEquipos;
import co.edu.unbosque.biohub.persistencia.usuarios.DirectorioPersonal;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Equipos de campo (RF-03, RF-04, HU-03, CU-03, CU-04, secuencia 6).
 *
 * Secuencia 6 del Entregable 2: el equipo se crea con su responsable, que se comprueba en
 * el repositorio de usuarios (C-2). El responsable debe existir, estar activo y ser
 * investigador (HU-03, criterio 1: "un investigador responsable").
 */
@Service
public class ServicioEquipos {

    private final RepositorioEquipos equipos;
    private final RepositorioUsuarios usuarios;
    private final DirectorioPersonal directorio;

    public ServicioEquipos(RepositorioEquipos equipos, RepositorioUsuarios usuarios, DirectorioPersonal directorio) {
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.directorio = directorio;
    }

    /** CU-03: registrar un equipo con su responsable. */
    @Transactional
    public VistaEquipo registrar(NuevoEquipo datos, long autorId) {
        EquipoCampo equipo = traducir(() ->
                EquipoCampo.crear(datos.codigo(), datos.tipo(), datos.descripcion(), datos.responsableId()));
        Usuario responsable = responsableValido(equipo.responsableId());
        if (equipos.existeCodigo(equipo.codigo())) {
            throw new ReglaDeNegocioException("El equipo no es válido",
                    List.of("codigo: ya existe un equipo con el código " + equipo.codigo()));
        }
        long id = equipos.registrar(equipo, autorId);
        return vista(equipo.conId(id), responsable);
    }

    /** CU-04 / RF-04: quién tiene el equipo, en cualquier momento. */
    public VistaEquipo detalle(long equipoId) {
        return vista(existente(equipoId));
    }

    public VistaEquipo porCodigo(String codigo) {
        String normal = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return vista(equipos.buscarPorCodigo(normal)
                .orElseThrow(() -> new NoEncontradoException("No existe el equipo " + normal)));
    }

    /** @param responsableId null para todos los equipos */
    public List<VistaEquipo> listar(Long responsableId) {
        Map<Long, Usuario> personas = new HashMap<>();
        return equipos.listar(responsableId).stream()
                .map(e -> vista(e, personas.computeIfAbsent(e.responsableId(), this::persona)))
                .toList();
    }

    @Transactional
    public VistaEquipo reasignar(long equipoId, Reasignacion datos, long autorId) {
        EquipoCampo actual = existente(equipoId);
        EquipoCampo nuevo = traducir(() -> actual.reasignarA(datos.responsableId(), datos.motivo()));
        Usuario responsable = responsableValido(nuevo.responsableId());
        boolean hecho = equipos.reasignar(equipoId, actual.responsableId(), nuevo.responsableId(), autorId,
                datos.motivo().trim());
        if (!hecho) {
            throw new ConflictoException("Otra persona cambió el responsable del equipo " + actual.codigo()
                    + " al mismo tiempo. Consulte de nuevo y reintente.");
        }
        return vista(nuevo, responsable);
    }

    public List<VistaAsignacion> asignaciones(long equipoId) {
        existente(equipoId);
        return equipos.asignaciones(equipoId).stream()
                .map(a -> new VistaAsignacion(a.responsableId(), a.responsableNombre(), a.asignadoPor(),
                        a.asignadoEn(), a.motivo()))
                .toList();
    }

    /** Para la lista de selección: los investigadores activos (HU-03, criterio 1). */
    public List<VistaPersona> responsablesPosibles() {
        return directorio.activos().stream()
                .filter(u -> u.rol() == Rol.INVESTIGADOR)
                .map(ServicioEquipos::persona)
                .toList();
    }

    private Usuario responsableValido(long id) {
        Usuario u = usuarios.buscarPorId(id).orElseThrow(() -> new ReglaDeNegocioException(
                "El responsable no es válido", List.of("responsableId: no existe el usuario " + id)));
        if (!u.activo()) {
            throw new ReglaDeNegocioException("El responsable no es válido",
                    List.of("responsableId: " + u.nombre() + " está inactivo"));
        }
        if (u.rol() != Rol.INVESTIGADOR) {   // HU-03, criterio 1: "un investigador responsable"
            throw new ReglaDeNegocioException("El responsable no es válido",
                    List.of("responsableId: " + u.nombre() + " no es investigador"));
        }
        return u;
    }

    private EquipoCampo existente(long equipoId) {
        return equipos.buscar(equipoId).orElseThrow(() -> new NoEncontradoException("No existe el equipo " + equipoId));
    }

    private Usuario persona(long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new IllegalStateException("Responsable " + id + " no existe"));
    }

    private VistaEquipo vista(EquipoCampo e) {
        return vista(e, persona(e.responsableId()));
    }

    private static VistaEquipo vista(EquipoCampo e, Usuario r) {
        return new VistaEquipo(e.id(), e.codigo(), e.tipo().name(), e.descripcion(), persona(r));
    }

    private static VistaPersona persona(Usuario u) {
        return new VistaPersona(u.id(), u.nombre(), u.correo(), u.rol().name());
    }

    private static EquipoCampo traducir(Supplier<EquipoCampo> paso) {
        try {
            return paso.get();
        } catch (ReglaDeEquipoException e) {
            throw new ReglaDeNegocioException("El equipo no es válido", e.getErrores());
        }
    }
}
