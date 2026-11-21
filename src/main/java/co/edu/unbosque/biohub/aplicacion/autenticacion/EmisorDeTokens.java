package co.edu.unbosque.biohub.aplicacion.autenticacion;

import co.edu.unbosque.biohub.dominio.usuarios.Usuario;

/**
 * Contrato para emitir el token de sesión (principio D de SOLID).
 * La aplicación no sabe que por debajo es un JWT: la implementación vive en
 * "configuracion" y en las pruebas unitarias se usa un stub.
 */
public interface EmisorDeTokens {

    TokenEmitido emitir(Usuario usuario);

    record TokenEmitido(String valor, long expiraEnSegundos) {
    }
}
