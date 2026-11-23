package co.edu.unbosque.biohub.dominio.usuarios;

/** Qué le pasó a un usuario. Cada cambio queda en la bitácora (V11). */
public enum TipoCambioUsuario {
    ALTA,
    CAMBIO_ROL,
    DESACTIVACION,
    REACTIVACION,
    CAMBIO_CLAVE
}
