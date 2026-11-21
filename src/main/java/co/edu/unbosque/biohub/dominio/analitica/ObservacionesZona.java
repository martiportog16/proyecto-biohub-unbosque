package co.edu.unbosque.biohub.dominio.analitica;

import java.util.List;

/**
 * Lo que se observó en una zona (departamento, P-26):
 *  - cuántas especies DISTINTAS se vieron en el mes del informe, y
 *  - cuántas se vieron en cada uno de los meses anteriores (hasta 3).
 * Un mes sin ningún hallazgo cuenta como 0 especies.
 */
public record ObservacionesZona(String departamento, int especiesEnElMes, List<Integer> especiesMesesAnteriores) {

    public ObservacionesZona {
        especiesMesesAnteriores = List.copyOf(especiesMesesAnteriores);
    }
}
