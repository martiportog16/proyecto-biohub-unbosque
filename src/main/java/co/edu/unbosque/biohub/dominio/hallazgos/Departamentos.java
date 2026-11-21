package co.edu.unbosque.biohub.dominio.hallazgos;

import java.util.Set;

/**
 * Los 32 departamentos de Colombia y Bogotá D.C.
 * El departamento es lo único de la ubicación que ve el público (RNF-06) y es
 * la "zona" del índice de presión mensual (P-26).
 */
public final class Departamentos {

    public static final Set<String> TODOS = Set.of(
            "Amazonas", "Antioquia", "Arauca", "Atlántico", "Bogotá D.C.", "Bolívar", "Boyacá",
            "Caldas", "Caquetá", "Casanare", "Cauca", "Cesar", "Chocó", "Córdoba", "Cundinamarca",
            "Guainía", "Guaviare", "Huila", "La Guajira", "Magdalena", "Meta", "Nariño",
            "Norte de Santander", "Putumayo", "Quindío", "Risaralda", "San Andrés y Providencia",
            "Santander", "Sucre", "Tolima", "Valle del Cauca", "Vaupés", "Vichada");

    private Departamentos() {
    }

    public static boolean esValido(String departamento) {
        return departamento != null && TODOS.contains(departamento);
    }
}
