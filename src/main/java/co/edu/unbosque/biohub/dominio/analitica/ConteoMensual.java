package co.edu.unbosque.biohub.dominio.analitica;

import java.time.YearMonth;

/**
 * Resultado de la consulta pesada del batch: cuántas especies DISTINTAS se
 * observaron en un departamento durante un mes.
 */
public record ConteoMensual(String departamento, YearMonth mes, int especies) {
}
