package co.edu.unbosque.biohub.dominio.analitica;

/**
 * Índice de presión de una zona en un mes: de 0 (sin presión) a 1 (no se vio ninguna especie).
 * "valor" y "base" son null cuando no hay datos para comparar.
 */
public record IndicePresion(String departamento, Double valor, int especiesEnElMes, Double base) {

    public boolean sinDatos() {
        return valor == null;
    }
}
