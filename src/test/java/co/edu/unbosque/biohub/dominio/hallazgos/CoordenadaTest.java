package co.edu.unbosque.biohub.dominio.hallazgos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Valores límite de la coordenada y del departamento (S20). */
class CoordenadaTest {

    @Test
    void losBordesExactosSonValidos() {
        assertThat(new Coordenada(90, 180, 1).errores()).isEmpty();
        assertThat(new Coordenada(-90, -180, null).errores()).isEmpty();
    }

    @Test
    void justoFueraDelBordeEsInvalido() {
        assertThat(new Coordenada(90.000001, 0, null).errores()).hasSize(1);
        assertThat(new Coordenada(0, -180.000001, null).errores()).hasSize(1);
    }

    @Test
    void laPrecisionDebeSerPositiva() {
        assertThat(new Coordenada(4.6, -74.1, 0).errores()).containsExactly(
                "coordenada.precisionMetros: debe ser mayor que 0");
    }

    @Test
    void elDepartamentoDebeExistirConSuNombreExacto() {
        LocalDate hoy = LocalDate.of(2026, 11, 15);
        assertThat(Hallazgo.erroresDeDatos(hoy, "Bogotá D.C.", hoy)).isEmpty();
        assertThat(Hallazgo.erroresDeDatos(hoy, "bogota", hoy)).hasSize(1);
    }
}
