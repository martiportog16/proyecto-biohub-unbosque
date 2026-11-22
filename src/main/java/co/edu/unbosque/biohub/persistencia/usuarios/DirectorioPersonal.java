package co.edu.unbosque.biohub.persistencia.usuarios;

import java.util.List;

import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

/**
 * La lista del personal activo, para escoger un responsable (por ejemplo, de un equipo de campo).
 * Va en una interfaz aparte de RepositorioUsuarios (segregación de interfaces): el inicio de
 * sesión y la autorización no necesitan listar a nadie.
 */
public interface DirectorioPersonal {

    List<Usuario> activos();
}
