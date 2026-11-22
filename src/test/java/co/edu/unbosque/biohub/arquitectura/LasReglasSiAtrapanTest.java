package co.edu.unbosque.biohub.arquitectura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import co.edu.unbosque.biohub.arquitectura.tramposo.ReporteTramposo;

/**
 * ¿Y cómo sabemos que las reglas de arquitectura funcionan? (S20: una prueba que
 * nunca falla no prueba nada.)
 *
 * Se les muestra una clase que viola la regla a propósito (ReporteTramposo) y se
 * comprueba que la detectan y dicen por qué.
 */
class LasReglasSiAtrapanTest {

    private final JavaClasses tramposa = new ClassFileImporter().importClasses(ReporteTramposo.class);

    @Test
    void unaClaseQueLeeCoordenadasSinAutorizacionHaceFallarLaRegla() {
        EvaluationResult resultado = SeguridadArquitecturaTest.soloLaAutorizacionLeeCoordenadas.evaluate(tramposa);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().toString())
                .contains("ReporteTramposo")
                .contains("LecturaCoordenadas")
                .contains("AutorizacionCoordenadas");
    }

    @Test
    void tambienLaAtrapaPorUsarLaUbicacionExacta() {
        EvaluationResult resultado = SeguridadArquitecturaTest.laUbicacionExactaNoSaleDeLaAutorizacion.evaluate(tramposa);

        assertThat(resultado.hasViolation()).isTrue();
    }

    @Test
    void elCodigoRealDeLaAplicacionNoTieneViolaciones() {
        JavaClasses aplicacion = new ClassFileImporter()
                .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("co.edu.unbosque.biohub");

        assertThat(SeguridadArquitecturaTest.soloLaAutorizacionLeeCoordenadas.evaluate(aplicacion).hasViolation()).isFalse();
    }
}
