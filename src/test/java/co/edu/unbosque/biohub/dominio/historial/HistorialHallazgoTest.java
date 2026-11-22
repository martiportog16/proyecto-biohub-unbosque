package co.edu.unbosque.biohub.dominio.historial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;

/**
 * Reglas del historial sin base de datos (S20: AAA, un comportamiento por prueba).
 * Hallazgo 1, registrado como especie 10.
 */
class HistorialHallazgoTest {

    private static final Instant REGISTRO = Instant.parse("2026-10-01T12:00:00Z");

    private static Hallazgo hallazgo(long especieActual) {
        return new Hallazgo(1L, especieActual, 10L, 1, Map.of(), LocalDate.of(2026, 9, 30),
                "Cundinamarca", 5L, REGISTRO);
    }

    private static Reclasificacion cambio(long id, long de, long a, String fecha) {
        return new Reclasificacion(id, 1L, de, a, "Nueva evidencia genética", 3L, Instant.parse(fecha));
    }

    @Test
    void sinReclasificacionesLaEspecieActualEsLaDelRegistro() {
        HistorialHallazgo historial = HistorialHallazgo.de(hallazgo(10L), List.of());

        assertThat(historial.entradas()).isEmpty();
        assertThat(historial.especieActualId()).isEqualTo(10L);
    }

    @Test
    void ordenaLasEntradasCronologicamenteAunqueLleguenDesordenadas() {
        Reclasificacion primera = cambio(1, 10, 20, "2026-10-05T10:00:00Z");
        Reclasificacion segunda = cambio(2, 20, 30, "2026-10-20T10:00:00Z");

        HistorialHallazgo historial = HistorialHallazgo.de(hallazgo(30L), List.of(segunda, primera));

        assertThat(historial.entradas()).containsExactly(primera, segunda);
        assertThat(historial.especieInicialId()).isEqualTo(10L);
        assertThat(historial.especieActualId()).isEqualTo(30L);
    }

    @Test
    void agregarDevuelveUnHistorialNuevoYNoCambiaElOriginal() {
        HistorialHallazgo original = HistorialHallazgo.de(hallazgo(10L), List.of());

        HistorialHallazgo nuevo = original.agregar(cambio(1, 10, 20, "2026-10-05T10:00:00Z"));

        assertThat(original.entradas()).isEmpty();
        assertThat(nuevo.entradas()).hasSize(1);
        assertThat(nuevo.especieActualId()).isEqualTo(20L);
    }

    @Test
    void laListaDeEntradasNoSePuedeModificarDesdeAfuera() {
        HistorialHallazgo historial = HistorialHallazgo.de(hallazgo(20L),
                List.of(cambio(1, 10, 20, "2026-10-05T10:00:00Z")));

        assertThatThrownBy(() -> historial.entradas().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> historial.entradas().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unCambioDebePartirDeLaEspecieActual() {
        HistorialHallazgo historial = HistorialHallazgo.de(hallazgo(10L), List.of());

        assertThatThrownBy(() -> historial.agregar(cambio(1, 99, 20, "2026-10-05T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("especie actual");
    }

    @Test
    void noSeAgreganCambiosConFechaAnteriorAlUltimo() {
        HistorialHallazgo historial = HistorialHallazgo.de(hallazgo(20L),
                List.of(cambio(1, 10, 20, "2026-10-20T10:00:00Z")));

        assertThatThrownBy(() -> historial.agregar(cambio(2, 20, 30, "2026-10-05T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elMotivoEsObligatorio() {
        assertThat(Reclasificacion.errores(10L, 20L, null)).containsExactly("motivo: es obligatorio");
        assertThat(Reclasificacion.errores(10L, 20L, "   ")).containsExactly("motivo: es obligatorio");
    }

    @Test
    void elMotivoTieneUnLargoMinimoYSeCuentaSinEspaciosALosLados() {
        // Valores límite (S20): 9 caracteres falla, 10 pasa.
        assertThat(Reclasificacion.errores(10L, 20L, "  123456789  ")).hasSize(1);
        assertThat(Reclasificacion.errores(10L, 20L, "1234567890")).isEmpty();
    }

    @Test
    void noSePuedeReclasificarALaMismaEspecieYSeReunenTodosLosErrores() {
        assertThat(Reclasificacion.errores(10L, 10L, ""))
                .containsExactly("especieNuevaId: el hallazgo ya está clasificado en esa especie",
                        "motivo: es obligatorio");
        assertThat(Reclasificacion.errores(10L, null, "Nueva evidencia genética"))
                .containsExactly("especieNuevaId: es obligatoria");
    }
}
