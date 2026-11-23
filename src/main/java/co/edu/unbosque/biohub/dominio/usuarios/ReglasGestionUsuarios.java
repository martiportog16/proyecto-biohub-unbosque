package co.edu.unbosque.biohub.dominio.usuarios;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Quién puede administrar a quién (P-28, S-01). Lógica pura: se prueba sin base de datos.
 *
 *  - El ADMINISTRADOR DE LA PLATAFORMA crea usuarios, les cambia el rol y los desactiva
 *    o reactiva. No puede dar ni quitar el rol de alto rango.
 *  - El DIRECTOR CIENTÍFICO solo da o quita el rol de ALTO RANGO, y solo entre personal
 *    científico. Lo dice el supuesto S-01 del SRS: "el Instituto, a través de los directores
 *    científicos, define quién cuenta como personal de alto rango".
 *  - Nadie se cambia el rol ni se desactiva a sí mismo: así nadie se da más permisos ni
 *    deja la plataforma sin administrador por un clic equivocado.
 *
 * Hay dos clases de "no": permiso() dice si el autor puede hacerlo (403) y errores...()
 * dice si los datos tienen sentido (422).
 */
public final class ReglasGestionUsuarios {

    public static final int LARGO_MINIMO_MOTIVO = 5;
    public static final int LARGO_MINIMO_CLAVE = 10;
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ReglasGestionUsuarios() {
    }

    /* ---------- Permisos (403) ---------- */

    public static Optional<String> permisoParaCrear(Rol autor, Rol rolNuevo) {
        if (autor != Rol.ADMINISTRADOR_PLATAFORMA) {
            return Optional.of("Solo el administrador de la plataforma crea usuarios");
        }
        if (rolNuevo == Rol.ALTO_RANGO) {
            return Optional.of("El rol de alto rango lo da un director científico (S-01): cree el usuario con su rol "
                    + "científico y pídale a un director que le dé el alto rango");
        }
        return Optional.empty();
    }

    public static Optional<String> permisoParaCambiarRol(Usuario autor, Usuario objetivo, Rol rolNuevo) {
        if (autor.id().equals(objetivo.id())) {
            return Optional.of("Nadie puede cambiar su propio rol");
        }
        if (involucraAltoRango(objetivo, rolNuevo)) {
            return autor.rol() == Rol.DIRECTOR_CIENTIFICO ? Optional.empty()
                    : Optional.of("Dar o quitar el rol de alto rango le corresponde a un director científico (S-01)");
        }
        return autor.rol() == Rol.ADMINISTRADOR_PLATAFORMA ? Optional.empty()
                : Optional.of("Solo el administrador de la plataforma cambia roles; el director científico solo da o quita el alto rango");
    }

    public static Optional<String> permisoParaCambiarEstado(Usuario autor, Usuario objetivo) {
        if (autor.rol() != Rol.ADMINISTRADOR_PLATAFORMA) {
            return Optional.of("Solo el administrador de la plataforma activa o desactiva usuarios");
        }
        if (autor.id().equals(objetivo.id())) {
            return Optional.of("Nadie puede desactivarse a sí mismo");
        }
        return Optional.empty();
    }

    /* ---------- Datos (422) ---------- */

    public static List<String> erroresDeAlta(String correo, String nombre, Rol rol, String motivo) {
        List<String> errores = new ArrayList<>();
        if (correo == null || correo.isBlank()) {
            errores.add("correo: es obligatorio");
        } else if (!CORREO.matcher(correo.trim()).matches() || correo.trim().length() > 120) {
            errores.add("correo: no es un correo válido");
        }
        if (nombre == null || nombre.isBlank()) {
            errores.add("nombre: es obligatorio");
        } else if (nombre.trim().length() > 120) {
            errores.add("nombre: máximo 120 caracteres");
        }
        if (rol == null) {
            errores.add("rol: es obligatorio");
        }
        errores.addAll(erroresDeMotivo(motivo));
        return errores;
    }

    public static List<String> erroresDeCambioDeRol(Usuario objetivo, Rol rolNuevo, String motivo) {
        List<String> errores = new ArrayList<>();
        if (rolNuevo == null) {
            errores.add("rol: es obligatorio");
        } else if (rolNuevo == objetivo.rol()) {
            errores.add("rol: " + objetivo.nombre() + " ya tiene ese rol");
        } else if (rolNuevo == Rol.ALTO_RANGO && !objetivo.rol().esCientifico()) {
            errores.add("rol: solo el personal científico puede ser de alto rango");
        } else if (objetivo.rol() == Rol.ALTO_RANGO && !rolNuevo.esCientifico()) {
            errores.add("rol: al quitar el alto rango, escoja un rol científico");
        }
        if (!objetivo.activo()) {
            errores.add("usuario: " + objetivo.nombre() + " está inactivo; reactívelo primero");
        }
        errores.addAll(erroresDeMotivo(motivo));
        return errores;
    }

    public static List<String> erroresDeCambioDeEstado(Usuario objetivo, Boolean activo, String motivo) {
        List<String> errores = new ArrayList<>();
        if (activo == null) {
            errores.add("activo: es obligatorio");
        } else if (activo == objetivo.activo()) {
            errores.add("activo: " + objetivo.nombre() + (activo ? " ya está activo" : " ya está inactivo"));
        }
        errores.addAll(erroresDeMotivo(motivo));
        return errores;
    }

    public static List<String> erroresDeClaveNueva(String claveNueva, String claveActual) {
        if (claveNueva == null || claveNueva.length() < LARGO_MINIMO_CLAVE) {
            return List.of("claveNueva: debe tener al menos " + LARGO_MINIMO_CLAVE + " caracteres");
        }
        if (claveNueva.equals(claveActual)) {
            return List.of("claveNueva: debe ser distinta de la actual");
        }
        return List.of();
    }

    private static List<String> erroresDeMotivo(String motivo) {
        return motivo == null || motivo.trim().length() < LARGO_MINIMO_MOTIVO
                ? List.of("motivo: es obligatorio (al menos " + LARGO_MINIMO_MOTIVO + " caracteres)")
                : List.of();
    }

    private static boolean involucraAltoRango(Usuario objetivo, Rol rolNuevo) {
        return rolNuevo == Rol.ALTO_RANGO || objetivo.rol() == Rol.ALTO_RANGO;
    }
}
