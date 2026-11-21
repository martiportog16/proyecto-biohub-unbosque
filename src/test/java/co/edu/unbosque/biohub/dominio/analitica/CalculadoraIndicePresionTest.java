package co.edu.unbosque.biohub.dominio.analitica;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * La fórmula del índice de presión (P-26) con sus casos de borde (S20).
 * B = promedio de los meses anteriores; índice = max(0, (B - E) / B).
 */
class CalculadoraIndicePresionTest {

    private final CalculadoraIndicePresion calculadora = new CalculadoraIndicePresion();

    private IndicePresion indice(int especiesMes, Integer... anteriores) {
        return calculadora.calcular(new ObservacionesZona("Meta", especiesMes, List.of(anteriores)));
    }

    @Test
    void ejemploDelDocumento() {
        // B = (10 + 12 + 8) / 3 = 10. E = 6. Índice = (10 - 6) / 10 = 0,4
        IndicePresion r = indice(6, 10, 12, 8);
        assertThat(r.valor()).isEqualTo(0.4);
        assertThat(r.base()).isEqualTo(10.0);
    }

    @Test
    void siSeVeLoMismoDeSiempreLaPresionEsCero() {
        assertThat(indice(10, 10, 10, 10).valor()).isEqualTo(0.0);
    }

    @Test
    void siSeVeMasDeLoHabitualNoEsNegativo() {
        assertThat(indice(15, 10, 10, 10).valor()).isEqualTo(0.0);
    }

    @Test
    void siNoSeVeNingunaEspecieLaPresionEsUno() {
        assertThat(indice(0, 4, 5, 6).valor()).isEqualTo(1.0);
    }

    @Test
    void siLosMesesAnterioresSonCeroNoHayDatos() {
        IndicePresion r = indice(3, 0, 0, 0);
        assertThat(r.sinDatos()).isTrue();
        assertThat(r.especiesEnElMes()).isEqualTo(3);
    }

    @Test
    void sinMesesAnterioresNoHayDatos() {
        assertThat(indice(3).sinDatos()).isTrue();
    }

    @Test
    void conUnSoloMesAnteriorUsaEseMes() {
        assertThat(indice(3, 4).valor()).isEqualTo(0.25);
    }

    @Test
    void soloUsaLosTresMesesMasRecientes() {
        // el cuarto valor (100) se ignora: B = 10
        assertThat(indice(5, 10, 10, 10, 100).valor()).isEqualTo(0.5);
    }

    @Test
    void redondeaACuatroDecimales() {
        // B = 3, E = 2 -> 1/3 = 0,33333...
        assertThat(indice(2, 3, 3, 3).valor()).isEqualTo(0.3333);
    }

    @Test
    void soloSeGeneraElInformeDeUnMesQueYaTermino() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        assertThat(CalculadoraIndicePresion.sePuedeGenerar(YearMonth.of(2026, 10), hoy)).isTrue();
        assertThat(CalculadoraIndicePresion.sePuedeGenerar(YearMonth.of(2026, 11), hoy)).isFalse();
        assertThat(CalculadoraIndicePresion.sePuedeGenerar(YearMonth.of(2026, 12), hoy)).isFalse();
    }
}
