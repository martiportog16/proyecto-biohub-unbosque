package co.edu.unbosque.biohub.dominio.analitica;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * El informe mensual de presión (RF-14): un mes y el índice de cada departamento.
 * Una vez generado no cambia (la base tampoco deja editarlo ni borrarlo).
 */
public record InformeMensual(YearMonth periodo, Instant generadoEn, List<IndicePresion> indices) {

    public InformeMensual {
        indices = List.copyOf(indices);
    }
}
