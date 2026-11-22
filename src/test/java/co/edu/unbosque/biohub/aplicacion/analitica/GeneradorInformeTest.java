package co.edu.unbosque.biohub.aplicacion.analitica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.analitica.GeneradorInforme.Resultado;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.analitica.ConteoMensual;
import co.edu.unbosque.biohub.dominio.analitica.IndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.persistencia.analitica.LecturaAnalitica;
import co.edu.unbosque.biohub.soporte.RepositorioInformesEnMemoria;

/**
 * Generar un informe sin base de datos (S20, S21).
 * - La lectura de analítica es un SPY: anota qué meses le pidieron y cuántas veces.
 * - El repositorio de informes es un FAKE en memoria.
 * - El reloj está FIJO en el 16 de noviembre de 2026 (hora de Colombia).
 */
class GeneradorInformeTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    private final RepositorioInformesEnMemoria informes = new RepositorioInformesEnMemoria();
    private final List<String> lecturas = new ArrayList<>();
    private final LecturaAnalitica lecturaEspia = (desde, hasta) -> {
        lecturas.add(desde + " a " + hasta);
        return List.of(
                new ConteoMensual("Meta", YearMonth.of(2026, 7), 8), new ConteoMensual("Meta", YearMonth.of(2026, 8), 12),
                new ConteoMensual("Meta", YearMonth.of(2026, 9), 10), new ConteoMensual("Meta", OCTUBRE, 6),
                new ConteoMensual("Nariño", OCTUBRE, 3));
    };

    private GeneradorInforme generadorEl(String instante) {
        return new GeneradorInforme(lecturaEspia, informes, Clock.fixed(Instant.parse(instante), BOGOTA));
    }

    @Test
    void leeElMesYLosTresAnterioresCalculaYGuardaElInforme() {
        Resultado resultado = generadorEl("2026-11-16T15:00:00Z").generar(OCTUBRE);

        assertThat(resultado).isEqualTo(Resultado.GENERADO);
        assertThat(lecturas).containsExactly("2026-07 a 2026-10");
        InformeMensual guardado = informes.informes.get(OCTUBRE);
        assertThat(guardado.generadoEn()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));
        assertThat(guardado.indices()).containsExactly(
                new IndicePresion("Meta", 0.4, 6, 10.0),
                new IndicePresion("Nariño", null, 3, 0.0));
    }

    @Test
    void siElInformeYaExisteNoVuelveAHacerLaConsultaPesada() {
        GeneradorInforme generador = generadorEl("2026-11-16T15:00:00Z");
        generador.generar(OCTUBRE);

        Resultado segundo = generador.generar(OCTUBRE);

        assertThat(segundo).isEqualTo(Resultado.YA_EXISTIA);
        assertThat(lecturas).as("solo se leyó la réplica una vez").hasSize(1);
    }

    @Test
    void sinNingunHallazgoNoSeGuardaUnInformeVacio() {
        // Un informe no se puede corregir después: uno vacío quedaría vacío para siempre
        GeneradorInforme generador = new GeneradorInforme((desde, hasta) -> List.of(), informes,
                Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), BOGOTA));

        assertThat(generador.generar(OCTUBRE)).isEqualTo(Resultado.SIN_DATOS);
        assertThat(informes.informes).isEmpty();
    }

    @Test
    void elMesEnCursoNoSeGeneraPorqueTodaviaRecibeHallazgos() {
        assertThatThrownBy(() -> generadorEl("2026-11-16T15:00:00Z").generar(YearMonth.of(2026, 11)))
                .isInstanceOf(ReglaDeNegocioException.class);

        assertThat(lecturas).isEmpty();
        assertThat(informes.informes).isEmpty();
    }

    @Test
    void elMesSePuedeGenerarDesdeElPrimerInstanteDelMesSiguienteEnHoraDeColombia() {
        // Valores límite (S20). 1 de noviembre 00:00 en Colombia = 05:00 UTC.
        assertThatThrownBy(() -> generadorEl("2026-11-01T04:59:59Z").generar(OCTUBRE))
                .as("31 de octubre 23:59:59 en Colombia: octubre no ha terminado")
                .isInstanceOf(ReglaDeNegocioException.class);

        assertThat(generadorEl("2026-11-01T05:00:00Z").generar(OCTUBRE)).isEqualTo(Resultado.GENERADO);
    }

    @Test
    void siOtroProcesoLoGuardoMientrasCalculabaNoEsUnError() {
        RepositorioInformesEnMemoria conCarrera = new RepositorioInformesEnMemoria() {
            @Override
            public boolean existe(YearMonth periodo) {
                return false; // cuando preguntó, todavía no existía
            }
        };
        conCarrera.informes.put(OCTUBRE, new InformeMensual(OCTUBRE, Instant.EPOCH, List.of()));
        GeneradorInforme generador = new GeneradorInforme(lecturaEspia, conCarrera,
                Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), BOGOTA));

        assertThat(generador.generar(OCTUBRE)).isEqualTo(Resultado.YA_EXISTIA);
        assertThat(conCarrera.informes.get(OCTUBRE).generadoEn()).as("no pisa el que ya estaba").isEqualTo(Instant.EPOCH);
    }
}
