package co.edu.unbosque.biohub.dominio.analitica;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * De los conteos de la base a las zonas de la fórmula (S20: lógica pura, AAA).
 * El informe es el de octubre de 2026; los meses anteriores son septiembre, agosto y julio.
 */
class ZonasDelPeriodoTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    private static ConteoMensual conteo(String departamento, int mes, int especies) {
        return new ConteoMensual(departamento, YearMonth.of(2026, mes), especies);
    }

    @Test
    void losMesesAnterioresVanDelMasRecienteAlMasAntiguo() {
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(
                conteo("Meta", 7, 8), conteo("Meta", 8, 12), conteo("Meta", 9, 10), conteo("Meta", 10, 6)));

        assertThat(zonas).containsExactly(new ObservacionesZona("Meta", 6, List.of(10, 12, 8)));
    }

    @Test
    void unMesSinHallazgosCuentaComoCero() {
        // La base no devuelve fila para agosto: no hubo hallazgos
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(
                conteo("Meta", 7, 3), conteo("Meta", 9, 5), conteo("Meta", 10, 4)));

        assertThat(zonas).containsExactly(new ObservacionesZona("Meta", 4, List.of(5, 0, 3)));
    }

    @Test
    void unDepartamentoQueSoloTieneMesesAnterioresApareceConCeroEspeciesEsteMes() {
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(conteo("Huila", 9, 2)));

        assertThat(zonas).containsExactly(new ObservacionesZona("Huila", 0, List.of(2, 0, 0)));
    }

    @Test
    void unDepartamentoNuevoNoTieneContraQueComparar() {
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(conteo("Nariño", 10, 5)));

        assertThat(zonas).containsExactly(new ObservacionesZona("Nariño", 5, List.of(0, 0, 0)));
    }

    @Test
    void ignoraLosConteosDeMesesFueraDeLaVentana() {
        // Valores límite (S20): junio queda fuera, julio entra; noviembre queda fuera
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(
                conteo("Meta", 6, 99), conteo("Meta", 7, 1), conteo("Meta", 11, 99)));

        assertThat(zonas).containsExactly(new ObservacionesZona("Meta", 0, List.of(0, 0, 1)));
    }

    @Test
    void lasZonasSalenEnOrdenAlfabetico() {
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(
                conteo("Meta", 10, 1), conteo("Cauca", 10, 1), conteo("Huila", 10, 1)));

        assertThat(zonas).extracting(ObservacionesZona::departamento).containsExactly("Cauca", "Huila", "Meta");
    }

    @Test
    void sinHallazgosNoHayZonas() {
        assertThat(ZonasDelPeriodo.armar(OCTUBRE, List.of())).isEmpty();
    }

    @Test
    void laVentanaCruzaElCambioDeAno() {
        YearMonth enero = YearMonth.of(2026, 1);

        assertThat(ZonasDelPeriodo.primerMesNecesario(enero)).isEqualTo(YearMonth.of(2025, 10));
        assertThat(ZonasDelPeriodo.armar(enero, List.of(
                new ConteoMensual("Meta", YearMonth.of(2025, 12), 7),
                new ConteoMensual("Meta", YearMonth.of(2025, 10), 3))))
                .containsExactly(new ObservacionesZona("Meta", 0, List.of(7, 0, 3)));
    }

    @Test
    void juntoConLaFormulaDaElEjemploDelDocumento() {
        // B = (10 + 12 + 8) / 3 = 10; E = 6; índice = 0,4
        List<ObservacionesZona> zonas = ZonasDelPeriodo.armar(OCTUBRE, List.of(
                conteo("Meta", 7, 8), conteo("Meta", 8, 12), conteo("Meta", 9, 10), conteo("Meta", 10, 6)));

        assertThat(new CalculadoraIndicePresion().calcularTodas(zonas))
                .containsExactly(new IndicePresion("Meta", 0.4, 6, 10.0));
    }
}
