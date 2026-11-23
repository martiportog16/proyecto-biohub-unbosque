package co.edu.unbosque.biohub.aplicacion.usuarios;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.comun.SinPermisoException;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeClave;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeEstado;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.CambioDeRol;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.NuevoUsuario;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.UsuarioCreado;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.VistaCambio;
import co.edu.unbosque.biohub.aplicacion.usuarios.VistasUsuarios.VistaUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.CambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.ReglasGestionUsuarios;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.TipoCambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.usuarios.AdministracionUsuarios;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Gestión de usuarios (P-28): crear, cambiar el rol, activar o desactivar, y cambiar la clave propia.
 *
 * Quién puede qué lo decide ReglasGestionUsuarios (dominio). Aquí se hace una cosa más por
 * seguridad: el rol del AUTOR se lee de la base, no del token. Si a alguien le quitaron el
 * rol hace 5 minutos, su token todavía dice el anterior (vale 60 min), pero aquí ya no le sirve.
 *
 * Cada cambio queda en la bitácora de solo inserción (V11), en la misma transacción.
 */
@Service
public class ServicioUsuarios {

    private static final SecureRandom AZAR = new SecureRandom();
    /** Sin letras ni números que se confundan al dictarlos (0/O, 1/l/I). */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    static final int LARGO_CLAVE_TEMPORAL = 14;

    private final RepositorioUsuarios usuarios;
    private final AdministracionUsuarios administracion;
    private final PasswordEncoder codificador;

    public ServicioUsuarios(RepositorioUsuarios usuarios, AdministracionUsuarios administracion,
                            PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.administracion = administracion;
        this.codificador = codificador;
    }

    /** El administrador de la plataforma ve a todos; el director, solo al personal científico (al que le puede dar alto rango). */
    public List<VistaUsuario> listar(long autorId) {
        Usuario autor = autor(autorId);
        return administracion.todos().stream()
                .filter(u -> autor.rol() == Rol.ADMINISTRADOR_PLATAFORMA || u.rol().esCientifico())
                .map(VistaUsuario::de)
                .toList();
    }

    public VistaUsuario detalle(long usuarioId, long autorId) {
        Usuario autor = autor(autorId);
        Usuario u = existente(usuarioId);
        if (autor.rol() != Rol.ADMINISTRADOR_PLATAFORMA && !u.rol().esCientifico()) {
            throw new NoEncontradoException("No existe el usuario " + usuarioId);
        }
        return VistaUsuario.de(u);
    }

    public List<VistaCambio> cambios(long usuarioId, long autorId) {
        detalle(usuarioId, autorId);   // mismas reglas de visibilidad
        return administracion.cambios(usuarioId).stream().map(VistaCambio::de).toList();
    }

    @Transactional
    public UsuarioCreado crear(NuevoUsuario datos, long autorId) {
        Usuario autor = autor(autorId);
        Rol rol = rolDe(datos.rol());
        ReglasGestionUsuarios.permisoParaCrear(autor.rol(), rol).ifPresent(motivo -> {
            throw new SinPermisoException(motivo);
        });
        List<String> errores = new ArrayList<>(ReglasGestionUsuarios.erroresDeAlta(datos.correo(), datos.nombre(), rol, datos.motivo()));
        if (datos.rol() != null && !datos.rol().isBlank() && rol == null) {
            errores.replaceAll(e -> e.equals("rol: es obligatorio") ? "rol: no existe el rol " + datos.rol() : e);
        }
        String correo = datos.correo() == null ? null : datos.correo().trim().toLowerCase(Locale.ROOT);
        if (errores.isEmpty() && administracion.existeCorreo(correo)) {
            errores.add("correo: ya existe un usuario con el correo " + correo);
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El usuario no es válido", errores);
        }
        String claveTemporal = claveTemporal();
        long id = administracion.crear(correo, datos.nombre().trim(), codificador.encode(claveTemporal), rol);
        administracion.registrar(CambioUsuario.nuevo(id, autor.id(), TipoCambioUsuario.ALTA, null, rol, datos.motivo()));
        return new UsuarioCreado(new VistaUsuario(id, correo, datos.nombre().trim(), rol.name(), true), claveTemporal);
    }

    @Transactional
    public VistaUsuario cambiarRol(long usuarioId, CambioDeRol datos, long autorId) {
        Usuario autor = autor(autorId);
        Usuario objetivo = existente(usuarioId);
        Rol rolNuevo = rolDe(datos.rol());
        if (datos.rol() != null && !datos.rol().isBlank() && rolNuevo == null) {
            throw new ReglaDeNegocioException("El cambio de rol no es válido", List.of("rol: no existe el rol " + datos.rol()));
        }
        ReglasGestionUsuarios.permisoParaCambiarRol(autor, objetivo, rolNuevo).ifPresent(motivo -> {
            throw new SinPermisoException(motivo);
        });
        List<String> errores = ReglasGestionUsuarios.erroresDeCambioDeRol(objetivo, rolNuevo, datos.motivo());
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El cambio de rol no es válido", errores);
        }
        administracion.cambiarRol(usuarioId, rolNuevo);
        administracion.registrar(CambioUsuario.nuevo(usuarioId, autor.id(), TipoCambioUsuario.CAMBIO_ROL,
                objetivo.rol(), rolNuevo, datos.motivo()));
        return VistaUsuario.de(existente(usuarioId));
    }

    @Transactional
    public VistaUsuario cambiarEstado(long usuarioId, CambioDeEstado datos, long autorId) {
        Usuario autor = autor(autorId);
        Usuario objetivo = existente(usuarioId);
        ReglasGestionUsuarios.permisoParaCambiarEstado(autor, objetivo).ifPresent(motivo -> {
            throw new SinPermisoException(motivo);
        });
        List<String> errores = ReglasGestionUsuarios.erroresDeCambioDeEstado(objetivo, datos.activo(), datos.motivo());
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("El cambio de estado no es válido", errores);
        }
        administracion.cambiarActivo(usuarioId, datos.activo());
        administracion.registrar(CambioUsuario.nuevo(usuarioId, autor.id(),
                datos.activo() ? TipoCambioUsuario.REACTIVACION : TipoCambioUsuario.DESACTIVACION,
                objetivo.rol(), objetivo.rol(), datos.motivo()));
        return VistaUsuario.de(existente(usuarioId));
    }

    /** Cualquier usuario cambia SU clave; por ejemplo, la temporal que le dieron al crearlo. */
    @Transactional
    public void cambiarMiClave(long autorId, CambioDeClave datos) {
        Usuario yo = autor(autorId);
        if (datos.claveActual() == null || !codificador.matches(datos.claveActual(), yo.claveHash())) {
            throw new ReglaDeNegocioException("No se cambió la clave", List.of("claveActual: no coincide con su clave actual"));
        }
        List<String> errores = ReglasGestionUsuarios.erroresDeClaveNueva(datos.claveNueva(), datos.claveActual());
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("No se cambió la clave", errores);
        }
        administracion.cambiarClave(yo.id(), codificador.encode(datos.claveNueva()));
        administracion.registrar(CambioUsuario.nuevo(yo.id(), yo.id(), TipoCambioUsuario.CAMBIO_CLAVE,
                null, null, "El usuario cambió su clave"));
    }

    /** El autor tal como está HOY en la base (no como dice su token). */
    private Usuario autor(long autorId) {
        return usuarios.buscarPorId(autorId).filter(Usuario::activo)
                .orElseThrow(() -> new SinPermisoException("Su usuario ya no está activo"));
    }

    private Usuario existente(long usuarioId) {
        return usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new NoEncontradoException("No existe el usuario " + usuarioId));
    }

    private static Rol rolDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Rol.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String claveTemporal() {
        StringBuilder clave = new StringBuilder(LARGO_CLAVE_TEMPORAL);
        for (int i = 0; i < LARGO_CLAVE_TEMPORAL; i++) {
            clave.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return clave.toString();
    }
}
