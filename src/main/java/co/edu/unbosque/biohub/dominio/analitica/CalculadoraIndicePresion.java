package co.edu.unbosque.biohub.dominio.analitica;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Fórmula del índice de presión (P-26, decidida en el documento de decisiones):
 *
 *     IndicePresion(d, m) = max(0, (B - E) / B)
 *
 *   E = especies distintas observadas en el departamento d durante el mes m
 *   B = promedio de E en los meses anteriores disponibles (hasta 3)
 *
 * Idea en simple: si en una zona se ven MENOS especies de lo habitual, la presión sube.
 * Si B es 0 (o no hay meses anteriores), no hay contra qué comparar: "sin datos".
 *
 * Es lógica pura, sin base de datos: se prueba en milisegundos (S20).
 */
public class CalculadoraIndicePresion {

    public static final int MESES_DE_COMPARACION = 3;

    public IndicePresion calcular(ObservacionesZona zona) {
        List<Integer> anteriores = zona.especiesMesesAnteriores().stream()
                .limit(MESES_DE_COMPARACION)
                .toList();
        if (anteriores.isEmpty()) {
            return new IndicePresion(zona.departamento(), null, zona.especiesEnElMes(), null);
        }
        double base = anteriores.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (base == 0) {
            return new IndicePresion(zona.departamento(), null, zona.especiesEnElMes(), 0.0);
        }
        double indice = Math.max(0, (base - zona.especiesEnElMes()) / base);
        return new IndicePresion(zona.departamento(), redondear(indice), zona.especiesEnElMes(), redondear(base));
    }

    public List<IndicePresion> calcularTodas(List<ObservacionesZona> zonas) {
        return zonas.stream().map(this::calcular).toList();
    }

    /**
     * Solo se genera el informe de un mes que YA terminó: el mes en curso todavía
     * puede recibir hallazgos y el índice saldría engañosamente alto.
     */
    public static boolean sePuedeGenerar(YearMonth periodo, LocalDate hoy) {
        return periodo.isBefore(YearMonth.from(hoy));
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
