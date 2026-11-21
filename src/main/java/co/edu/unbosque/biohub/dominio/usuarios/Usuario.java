package co.edu.unbosque.biohub.dominio.usuarios;

/**
 * Usuario de BioHub. La clave nunca se guarda: solo su hash BCrypt.
 */
public record Usuario(Long id, String correo, String nombre, String claveHash, Rol rol, boolean activo) {

    public boolean puedeIniciarSesion() {
        return activo;
    }
}
