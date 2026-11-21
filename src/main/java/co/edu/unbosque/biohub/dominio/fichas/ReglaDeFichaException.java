package co.edu.unbosque.biohub.dominio.fichas;

import java.util.List;

/** Un cambio a la ficha viola una regla del dominio. Lleva todos los errores encontrados. */
public class ReglaDeFichaException extends RuntimeException {

    private final List<String> errores;

    public ReglaDeFichaException(List<String> errores) {
        super("La ficha no es válida");
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
