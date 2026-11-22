package co.edu.unbosque.biohub.dominio.analitica;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convierte los conteos que devuelve la base en las "zonas" que entiende la fórmula.
 *
 * La base solo devuelve filas para los meses en que SÍ hubo hallazgos. Aquí se
 * completan los huecos: un mes sin hallazgos en un departamento cuenta como 0 especies.
 *
 * Lógica pura (sin base de datos): se prueba sola.
 */
public final class ZonasDelPeriodo {

    private ZonasDelPeriodo() {
    }

    /** El primer mes que hay que leer para calcular el informe de "periodo". */
    public static YearMonth primerMesNecesario(YearMonth periodo) {
        return periodo.minusMonths(CalculadoraIndicePresion.MESES_DE_COMPARACION);
    }

    /**
     * Una zona por cada departamento con al menos un hallazgo entre el periodo y
     * sus 3 meses anteriores, en orden alfabético. Los meses anteriores van del
     * más reciente al más antiguo. Conteos de otros meses se ignoran.
     */
    public static List<ObservacionesZona> armar(YearMonth periodo, List<ConteoMensual> conteos) {
        YearMonth primero = primerMesNecesario(periodo);
        Map<String, Map<YearMonth, Integer>> porDepartamento = new TreeMap<>();
        for (ConteoMensual c : conteos) {
            if (c.mes().isBefore(primero) || c.mes().isAfter(periodo)) {
                continue;
            }
            porDepartamento.computeIfAbsent(c.departamento(), d -> new HashMap<>()).put(c.mes(), c.especies());
        }

        List<ObservacionesZona> zonas = new ArrayList<>();
        porDepartamento.forEach((departamento, meses) -> {
            List<Integer> anteriores = new ArrayList<>();
            for (int i = 1; i <= CalculadoraIndicePresion.MESES_DE_COMPARACION; i++) {
                anteriores.add(meses.getOrDefault(periodo.minusMonths(i), 0));
            }
            zonas.add(new ObservacionesZona(departamento, meses.getOrDefault(periodo, 0), anteriores));
        });
        return zonas;
    }
}
