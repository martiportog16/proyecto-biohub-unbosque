package co.edu.unbosque.biohub.dominio.hallazgos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Valores límite de la coordenada y del departamento (S20). */
class CoordenadaTest {

    @Test
    void losBordesDelRecuadroDeColombiaSonValidos() {
        assertThat(new Coordenada(-4.3, -82.0, 1).errores()).isEmpty();
        assertThat(new Coordenada(13.6, -66.8, null).errores()).isEmpty();
        assertThat(new Coordenada(12.58, -81.70, null).errores()).as("San Andrés").isEmpty();
        assertThat(new Coordenada(-4.21, -69.94, null).errores()).as("Leticia").isEmpty();
    }

    @Test
    void justoFueraDelRecuadroEsInvalido() {
        assertThat(new Coordenada(13.600001, -74, null).errores()).hasSize(1);
        assertThat(new Coordenada(4.6, -66.799999, null).errores()).hasSize(1);
    }

    @Test
    void losErroresComunesSeRechazan() {
        assertThat(new Coordenada(48.85, 2.35, null).errores()).as("París").hasSize(2);
        assertThat(new Coordenada(4.6, 74.1, null).errores()).as("longitud sin el signo menos").hasSize(1);
        assertThat(new Coordenada(-74.1, 4.6, null).errores()).as("latitud y longitud invertidas").hasSize(2);
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

    @Test
    void laFechaNoPuedeSerAnteriorA1900() {
        LocalDate hoy = LocalDate.of(2026, 11, 15);
        assertThat(Hallazgo.erroresDeDatos(LocalDate.of(1900, 1, 1), "Meta", hoy)).isEmpty();
        assertThat(Hallazgo.erroresDeDatos(LocalDate.of(1899, 12, 31), "Meta", hoy))
                .containsExactly("fechaObservacion: no puede ser anterior a 1900-01-01");
    }
}
