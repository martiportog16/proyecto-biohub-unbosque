package co.edu.unbosque.biohub.dominio.equipos;

import java.util.List;

/** Un equipo no cumple las reglas del dominio. Lleva todos los errores encontrados. */
public class ReglaDeEquipoException extends RuntimeException {

    private final List<String> errores;

    public ReglaDeEquipoException(List<String> errores) {
        super("El equipo no es válido");
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
