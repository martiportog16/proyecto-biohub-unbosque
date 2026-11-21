package co.edu.unbosque.biohub.aplicacion.comun;

import java.util.List;

/**
 * Se violó una regla del negocio (por ejemplo, una ficha con valores inválidos).
 * Lleva TODOS los errores encontrados, no solo el primero (HU-01, criterio 2).
 * La presentación lo traduce a HTTP 422.
 */
public class ReglaDeNegocioException extends RuntimeException {

    private final List<String> detalles;

    public ReglaDeNegocioException(String mensaje) {
        this(mensaje, List.of());
    }

    public ReglaDeNegocioException(String mensaje, List<String> detalles) {
        super(mensaje);
        this.detalles = List.copyOf(detalles);
    }

    public List<String> getDetalles() {
        return detalles;
    }
}
