package co.edu.unbosque.biohub.persistencia.usuarios;

import java.util.List;

import co.edu.unbosque.biohub.dominio.usuarios.CambioUsuario;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

/**
 * Lo que necesita la gestión de usuarios (P-28). Va aparte de RepositorioUsuarios
 * (segregación de interfaces, la I de SOLID): el inicio de sesión y la autorización de
 * coordenadas solo buscan usuarios; no pueden crearlos ni cambiarles el rol.
 */
public interface AdministracionUsuarios {

    List<Usuario> todos();

    boolean existeCorreo(String correo);

    long crear(String correo, String nombre, String claveHash, Rol rol);

    void cambiarRol(long usuarioId, Rol rol);

    void cambiarActivo(long usuarioId, boolean activo);

    void cambiarClave(long usuarioId, String claveHash);

    /** Bitácora de solo inserción (V11). */
    void registrar(CambioUsuario cambio);

    /** La bitácora de un usuario, de la más antigua a la más reciente. */
    List<CambioUsuario> cambios(long usuarioId);
}
