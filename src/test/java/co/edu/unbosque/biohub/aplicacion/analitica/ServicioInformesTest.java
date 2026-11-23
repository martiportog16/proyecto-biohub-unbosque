package co.edu.unbosque.biohub.aplicacion.analitica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.EstadoSolicitud;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaSolicitud;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.analitica.IndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.soporte.RepositorioInformesEnMemoria;

/** Lo que hace el analista desde la API, sin base de datos. Hoy es 16 de noviembre de 2026. */
class ServicioInformesTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final long ANALISTA = 6L;

    private final RepositorioInformesEnMemoria informes = new RepositorioInformesEnMemoria();
    private final ServicioInformes servicio = new ServicioInformes(informes,
            Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("America/Bogota")));

    @Test
    void pedirUnInformeSoloDejaUnaSolicitudPendiente() {
        VistaSolicitud solicitud = servicio.solicitar(OCTUBRE, ANALISTA);

        assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(informes.solicitudes).hasSize(1);
        assertThat(informes.informes).as("la API no calcula el informe").isEmpty();
    }

    @Test
    void unMesAnteriorA1900SeRechazaPorqueNoPuedeHaberHallazgos() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.solicitar(java.time.YearMonth.of(1899, 12), ANALISTA))
                .isInstanceOf(co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException.class)
                .hasFieldOrPropertyWithValue("detalles", java.util.List.of("periodo: no puede ser anterior a 1900-01"));
        assertThat(servicio.solicitar(java.time.YearMonth.of(1900, 1), ANALISTA).estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void pedirDosVecesElMismoMesNoDuplicaLaSolicitud() {
        VistaSolicitud primera = servicio.solicitar(OCTUBRE, ANALISTA);
        VistaSolicitud segunda = servicio.solicitar(OCTUBRE, ANALISTA);

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(informes.solicitudes).hasSize(1);
    }

    @Test
    void siElInformeYaExisteLoDiceYNoCreaSolicitud() {
        informes.guardar(new InformeMensual(OCTUBRE, Instant.EPOCH, List.of()));

        VistaSolicitud solicitud = servicio.solicitar(OCTUBRE, ANALISTA);

        assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.INFORME_YA_EXISTE);
        assertThat(informes.solicitudes).isEmpty();
    }

    @Test
    void noSePuedePedirElMesEnCursoNiDejarElMesVacio() {
        assertThatThrownBy(() -> servicio.solicitar(YearMonth.of(2026, 11), ANALISTA))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> servicio.solicitar(null, ANALISTA))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(informes.solicitudes).isEmpty();
    }

    @Test
    void elInformeMuestraCuandoUnDepartamentoQuedoSinDatos() {
        informes.guardar(new InformeMensual(OCTUBRE, Instant.EPOCH, List.of(
                new IndicePresion("Meta", 0.4, 6, 10.0), new IndicePresion("Nariño", null, 3, 0.0))));

        assertThat(servicio.informe(OCTUBRE).indices()).extracting(i -> i.sinDatos()).containsExactly(false, true);
        assertThat(servicio.recientes()).singleElement().satisfies(r -> assertThat(r.totalDepartamentos()).isEqualTo(2));
    }

    @Test
    void unaSolicitudAtendidaSinInformeSeMuestraComoSinDatosYSePuedeVolverAPedir() {
        servicio.solicitar(OCTUBRE, ANALISTA);
        informes.marcarAtendida(1L, Instant.parse("2026-11-16T15:01:00Z")); // el batch no encontró hallazgos

        assertThat(servicio.solicitudesRecientes()).singleElement()
                .satisfies(s -> assertThat(s.estado()).isEqualTo(EstadoSolicitud.SIN_DATOS));
        assertThat(servicio.solicitar(OCTUBRE, ANALISTA).id()).as("crea una solicitud nueva").isEqualTo(2L);
    }

    @Test
    void unMesSinInformeResponde404() {
        assertThatThrownBy(() -> servicio.informe(OCTUBRE)).isInstanceOf(NoEncontradoException.class);
    }
}
