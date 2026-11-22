package co.edu.unbosque.biohub.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import co.edu.unbosque.biohub.persistencia.coordenadas.EscrituraCoordenadas;
import co.edu.unbosque.biohub.persistencia.coordenadas.LecturaCoordenadas;
import co.edu.unbosque.biohub.persistencia.coordenadas.RegistroAccesos;

/**
 * PR-08 (RNF-06): la protección de coordenadas también se verifica en la ARQUITECTURA.
 *
 * El paquete 07 dejó LecturaCoordenadas con un solo cliente: la autorización.
 * Hasta ahora eso lo decía un comentario. Estas reglas lo convierten en algo que
 * se comprueba en cada "mvn verify": si alguien escribe otra clase que lea
 * coordenadas sin pasar por la autorización, las pruebas fallan y el CI queda en rojo.
 *
 * (Las reglas de capas cerradas y "el dominio no toca la base" están en CapasTest.)
 */
@AnalyzeClasses(packages = "co.edu.unbosque.biohub", importOptions = ImportOption.DoNotIncludeTests.class)
class SeguridadArquitecturaTest {

    static final String AUTORIZACION = "co.edu.unbosque.biohub.aplicacion.autorizacion..";
    static final String PERSISTENCIA_COORDENADAS = "co.edu.unbosque.biohub.persistencia.coordenadas..";

    /** C-3: la ÚNICA puerta para LEER coordenadas exactas es el componente de autorización. */
    @ArchTest
    static final ArchRule soloLaAutorizacionLeeCoordenadas = noClasses()
            .that().resideOutsideOfPackages(AUTORIZACION, PERSISTENCIA_COORDENADAS)
            .should().dependOnClassesThat().areAssignableTo(LecturaCoordenadas.class)
            .because("las coordenadas exactas solo se leen a través de AutorizacionCoordenadas (RNF-06, C-3)");

    /** El registro de accesos (P-18) solo lo escribe la autorización: nadie más puede "inventar" o tapar accesos. */
    @ArchTest
    static final ArchRule soloLaAutorizacionUsaElRegistroDeAccesos = noClasses()
            .that().resideOutsideOfPackages(AUTORIZACION, PERSISTENCIA_COORDENADAS)
            .should().dependOnClassesThat().areAssignableTo(RegistroAccesos.class)
            .because("cada acceso a coordenadas se registra en un solo lugar (P-18)");

    /** C-3: ESCRIBIR coordenadas solo lo hace el registro de hallazgos. */
    @ArchTest
    static final ArchRule soloElRegistroDeHallazgosEscribeCoordenadas = noClasses()
            .that().resideOutsideOfPackages("co.edu.unbosque.biohub.aplicacion.hallazgos..", PERSISTENCIA_COORDENADAS)
            .should().dependOnClassesThat().areAssignableTo(EscrituraCoordenadas.class)
            .because("las coordenadas solo se guardan al registrar un hallazgo (C-3)");

    /** Ninguna clase fuera de la autorización conoce la ubicación exacta armada para el mapa. */
    @ArchTest
    static final ArchRule laUbicacionExactaNoSaleDeLaAutorizacion = noClasses()
            .that().resideOutsideOfPackages(AUTORIZACION, PERSISTENCIA_COORDENADAS,
                    "co.edu.unbosque.biohub.dominio.coordenadas..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta")
            .because("solo la autorización convierte una ubicación exacta en respuesta (RNF-06)");

    /** El grupo público (consulta pública e integración externa) no toca nada de coordenadas. */
    @ArchTest
    static final ArchRule loPublicoNoTocaCoordenadas = noClasses()
            .that().resideInAnyPackage("co.edu.unbosque.biohub..consultapublica..", "co.edu.unbosque.biohub..integracion..")
            .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCIA_COORDENADAS, AUTORIZACION,
                    "co.edu.unbosque.biohub.dominio.coordenadas..")
            .because("el portal y la API externa solo muestran el departamento (HU-04, P-30)");
}
