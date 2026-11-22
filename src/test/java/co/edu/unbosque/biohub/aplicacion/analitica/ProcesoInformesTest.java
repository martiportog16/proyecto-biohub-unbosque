package co.edu.unbosque.biohub.aplicacion.analitica;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.dominio.analitica.ConteoMensual;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;
import co.edu.unbosque.biohub.persistencia.analitica.LecturaAnalitica;
import co.edu.unbosque.biohub.soporte.RepositorioInformesEnMemoria;

/**
 * Lo que hace el batch en cada vuelta, sin base de datos y sin esperar (S21: reloj fijo).
 * La lectura de analítica falla a propósito para junio de 2026 y no encuentra
 * hallazgos para marzo de 2026.
 */
class ProcesoInformesTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final YearMonth MES_QUE_FALLA = YearMonth.of(2026, 6);
    private static final YearMonth MES_SIN_HALLAZGOS = YearMonth.of(2026, 3);

    private final RepositorioInformesEnMemoria informes = new RepositorioInformesEnMemoria();
    private final List<YearMonth> lecturas = new ArrayList<>();
    private final LecturaAnalitica lectura = (desde, hasta) -> {
        if (hasta.equals(MES_QUE_FALLA)) {
            throw new IllegalStateException("La réplica de analítica no responde");
        }
        lecturas.add(hasta);
        return hasta.equals(MES_SIN_HALLAZGOS) ? List.of() : List.of(new ConteoMensual("Meta", hasta, 4));
    };

    private ProcesoInformes procesoEl(String instante) {
        Clock reloj = Clock.fixed(Instant.parse(instante), BOGOTA);
        return new ProcesoInformes(new GeneradorInforme(lectura, informes, reloj), informes, reloj);
    }

    private void pedir(YearMonth periodo) {
        informes.crearSolicitud(periodo, 6L, Instant.parse("2026-11-16T14:00:00Z"));
    }

    @Test
    void atiendeLasSolicitudesPendientesEnOrdenYLasMarca() {
        pedir(YearMonth.of(2026, 9));
        pedir(YearMonth.of(2026, 8));

        int atendidas = procesoEl("2026-11-16T15:00:00Z").atenderSolicitudes();

        assertThat(atendidas).isEqualTo(2);
        assertThat(lecturas).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 8));
        assertThat(informes.solicitudes).allSatisfy(s -> {
            assertThat(s.pendiente()).isFalse();
            assertThat(s.atendidaEn()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));
        });
        assertThat(informes.informes).containsOnlyKeys(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
    }

    @Test
    void siUnaSolicitudFallaQuedaPendienteYLasDemasSeAtienden() {
        pedir(MES_QUE_FALLA);
        pedir(YearMonth.of(2026, 9));

        int atendidas = procesoEl("2026-11-16T15:00:00Z").atenderSolicitudes();

        assertThat(atendidas).isEqualTo(1);
        assertThat(informes.solicitudes).extracting(SolicitudInforme::pendiente).containsExactly(true, false);
    }

    @Test
    void unaSolicitudQueNoSePuedeCumplirSeDescartaSinGenerarNada() {
        pedir(YearMonth.of(2026, 11)); // el mes en curso

        procesoEl("2026-11-16T15:00:00Z").atenderSolicitudes();

        assertThat(informes.solicitudes).singleElement().satisfies(s -> assertThat(s.pendiente()).isFalse());
        assertThat(informes.informes).isEmpty();
    }

    @Test
    void unMesSinHallazgosNoDejaInformeVacioNiSolicitudesEternas() {
        pedir(MES_SIN_HALLAZGOS);

        procesoEl("2026-11-16T15:00:00Z").atenderSolicitudes();

        assertThat(informes.informes).as("no se guarda un informe vacío").isEmpty();
        assertThat(informes.solicitudes).singleElement().satisfies(s -> assertThat(s.pendiente()).isFalse());
    }

    @Test
    void siYaExisteElInformeLaSolicitudSeMarcaAtendidaSinRecalcular() {
        procesoEl("2026-11-16T15:00:00Z").generarMesAnterior();
        pedir(YearMonth.of(2026, 10));

        procesoEl("2026-11-16T15:00:00Z").atenderSolicitudes();

        assertThat(lecturas).as("una sola consulta pesada").hasSize(1);
        assertThat(informes.solicitudes).singleElement().satisfies(s -> assertThat(s.pendiente()).isFalse());
    }

    @Test
    void laTareaMensualGeneraElInformeDelMesQueAcabaDeTerminar() {
        // Día 1 de noviembre, 2:00 a. m. en Colombia = 07:00 UTC
        procesoEl("2026-11-01T07:00:00Z").generarMesAnterior();

        assertThat(informes.informes).containsOnlyKeys(YearMonth.of(2026, 10));
    }

    @Test
    void ponerseAlDiaNoSeAdelantaALaHoraProgramada() {
        // Valores límite (S20): 1:59:59 a. m. todavía no; 2:00:00 a. m. sí
        procesoEl("2026-11-01T06:59:59Z").ponerseAlDia();
        assertThat(informes.informes).isEmpty();

        procesoEl("2026-11-01T07:00:00Z").ponerseAlDia();
        assertThat(informes.informes).containsOnlyKeys(YearMonth.of(2026, 10));
    }

    @Test
    void siElBatchEstuvoApagadoElDiaUnoGeneraElInformeAlVolver() {
        // El batch arranca el 16 de noviembre y el informe de octubre no existe
        ProcesoInformes proceso = procesoEl("2026-11-16T15:00:00Z");

        proceso.ponerseAlDia();
        proceso.ponerseAlDia();

        assertThat(informes.informes).containsOnlyKeys(YearMonth.of(2026, 10));
        assertThat(lecturas).as("la segunda revisión no recalcula").hasSize(1);
    }

    @Test
    void siLaBaseNoRespondeElBatchNoSeCaeYReintentaDespues() {
        pedir(YearMonth.of(2026, 9));
        ProcesoInformes proceso = procesoEl("2026-11-16T15:00:00Z");

        informes.caida = true;
        assertThat(proceso.atenderSolicitudes()).isZero();
        proceso.ponerseAlDia();
        proceso.generarMesAnterior();

        informes.caida = false;
        assertThat(proceso.atenderSolicitudes()).isEqualTo(1);
    }
}
