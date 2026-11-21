package co.edu.unbosque.biohub.persistencia.usuarios;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

/**
 * Repositorio de usuarios (C-2). La aplicación depende de esta interfaz, no de JDBC
 * (principio D de SOLID): en las pruebas unitarias se reemplaza por un doble.
 */
public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorCorreo(String correo);

    Optional<Usuario> buscarPorId(long id);
}
