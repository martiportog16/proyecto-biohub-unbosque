package co.edu.unbosque.biohub.dominio.usuarios;

/**
 * Los perfiles del SRS v3.0 que inician sesión (el visitante no inicia sesión).
 * Cada usuario tiene uno solo (P-25).
 */
public enum Rol {

    INVESTIGADOR(true),
    ADMINISTRADOR_CIENTIFICO(true),
    DIRECTOR_CIENTIFICO(true),
    AUDITOR_CIENTIFICO(true),
    ALTO_RANGO(true),
    ANALISTA(false),
    ADMINISTRADOR_PLATAFORMA(false);

    private final boolean cientifico;

    Rol(boolean cientifico) {
        this.cientifico = cientifico;
    }

    /** Personal científico: puede ver coordenadas de especies FUERA de peligro (P-24). */
    public boolean esCientifico() {
        return cientifico;
    }
}
