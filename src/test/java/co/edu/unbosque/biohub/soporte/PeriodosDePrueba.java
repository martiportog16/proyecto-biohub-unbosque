package co.edu.unbosque.biohub.soporte;

import java.time.YearMonth;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Meses para las pruebas de informes. Un informe no se puede borrar ni volver a
 * generar, así que cada prueba necesita un mes que nadie más haya usado.
 * Se usan meses muy antiguos (1900-1999) donde no hay hallazgos de otras pruebas.
 */
public final class PeriodosDePrueba {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1900 * 12, 1990 * 12));

    private PeriodosDePrueba() {
    }

    /** Un mes distinto en cada llamada, separado 6 meses del anterior para que no compartan datos. */
    public static YearMonth nuevo() {
        int meses = SIGUIENTE.getAndAdd(6);
        return YearMonth.of(meses / 12, meses % 12 + 1);
    }
}
