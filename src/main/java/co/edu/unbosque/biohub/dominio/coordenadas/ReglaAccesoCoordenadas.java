package co.edu.unbosque.biohub.dominio.coordenadas;

import co.edu.unbosque.biohub.dominio.usuarios.Rol;

/**
 * LA regla de quién ve coordenadas exactas (P-24, RNF-06). Está en un solo lugar.
 *
 *   Especie EN peligro     -> solo el personal científico de alto rango.
 *   Especie FUERA de peligro -> cualquier rol científico.
 *   Sin rol (visitante, consumidor externo), rol no científico o usuario inactivo -> nunca.
 *
 * Es lógica pura: no toca la base de datos ni la web, así que se prueba sola (PR-14).
 * Son dos casos, por eso es un "if" claro y no un patrón Strategy (M-5 descartado).
 */
public final class ReglaAccesoCoordenadas {

    private ReglaAccesoCoordenadas() {
    }

    /** Por qué se permitió o se negó. Se guarda en el registro de accesos (P-18). */
    public enum Motivo {
        ALTO_RANGO_ESPECIE_EN_PELIGRO(true, "Personal de alto rango consultando una especie en peligro"),
        CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO(true, "Rol científico consultando una especie fuera de peligro"),
        ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO(false,
                "Las coordenadas exactas de una especie en peligro solo las ve el personal científico de alto rango"),
        ROL_NO_CIENTIFICO(false, "Solo el personal científico puede ver coordenadas exactas"),
        USUARIO_INACTIVO(false, "El usuario está inactivo");

        private final boolean permite;
        private final String descripcion;

        Motivo(boolean permite, String descripcion) {
            this.permite = permite;
            this.descripcion = descripcion;
        }

        public boolean permite() {
            return permite;
        }

        public String descripcion() {
            return descripcion;
        }
    }

    /**
     * @param rol            rol del usuario; null si no tiene (visitante o consumidor externo)
     * @param usuarioActivo  si el usuario sigue activo en el Instituto
     * @param especieEnPeligro si la especie ACTUAL del hallazgo está en peligro
     */
    public static Motivo evaluar(Rol rol, boolean usuarioActivo, boolean especieEnPeligro) {
        if (rol == null || !rol.esCientifico()) {
            return Motivo.ROL_NO_CIENTIFICO;
        }
        if (!usuarioActivo) {
            return Motivo.USUARIO_INACTIVO;
        }
        if (especieEnPeligro) {
            return rol == Rol.ALTO_RANGO
                    ? Motivo.ALTO_RANGO_ESPECIE_EN_PELIGRO
                    : Motivo.ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO;
        }
        return Motivo.CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO;
    }
}
