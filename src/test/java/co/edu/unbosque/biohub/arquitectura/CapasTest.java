package co.edu.unbosque.biohub.arquitectura;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Prueba de arquitectura (PR-08, ADR-02): las capas son CERRADAS.
 *
 *   presentacion -> aplicacion -> persistencia -> (base de datos)
 *                        |              |
 *                        +--> dominio <-+
 *
 * - La presentación solo habla con la aplicación.
 * - El dominio no conoce a nadie: no accede a la base de datos.
 * - "configuracion" es transversal (seguridad, beans) y puede ver todo.
 */
@AnalyzeClasses(packages = "co.edu.unbosque.biohub", importOptions = ImportOption.DoNotIncludeTests.class)
class CapasTest {

    private static final String BASE = "co.edu.unbosque.biohub";

    @ArchTest
    static final ArchRule capasCerradas = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Presentacion").definedBy(BASE + ".presentacion..")
            .layer("Aplicacion").definedBy(BASE + ".aplicacion..")
            .layer("Dominio").definedBy(BASE + ".dominio..")
            .layer("Persistencia").definedBy(BASE + ".persistencia..")
            .layer("Configuracion").definedBy(BASE + ".configuracion..")
            .whereLayer("Presentacion").mayOnlyBeAccessedByLayers("Configuracion")
            .whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Presentacion", "Configuracion")
            .whereLayer("Persistencia").mayOnlyBeAccessedByLayers("Aplicacion", "Configuracion")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Persistencia", "Configuracion");

    @ArchTest
    static final ArchRule elDominioNoTocaLaBaseDeDatos = noClasses()
            .that().resideInAPackage(BASE + ".dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.springframework.jdbc..")
            .allowEmptyShould(true);
}
