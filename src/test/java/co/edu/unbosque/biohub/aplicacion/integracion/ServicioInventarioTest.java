package co.edu.unbosque.biohub.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario.ConsultaInventario;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioInventario.PaginaInventario;
import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioInventario;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioInventario.FilaInventario;

/**
 * Paginación y filtro de atributos, sin base de datos.
 * El inventario fake tiene "total" hallazgos con ids 1..total.
 * La ficha cuenta cuántas veces se le pide (spy).
 */
class ServicioInventarioTest {

    private static final ConsultaInventario SIN_FILTROS = new ConsultaInventario(null, null, null, null);

    private int lecturasDeFicha;
    private final List<Long> cursoresPedidos = new ArrayList<>();

    private ServicioInventario servicioCon(int total) {
        RepositorioInventario inventario = (filtros, despuesDe, limite) -> {
            cursoresPedidos.add(despuesDe);
            return LongStream.rangeClosed(despuesDe + 1, Math.min(total, despuesDe + limite))
                    .mapToObj(id -> new FilaInventario(id, 2, "Tremarctos ornatus", "Oso andino", LocalDate.of(2026, 11, 1),
                            "Meta", 2, 1, Map.of("ancho_huella", 14.5, "ruta", List.of(Map.of("lat", 4.7, "lon", -74.0)))))
                    .toList();
        };
        FichaTaxonomica ficha = FichaTaxonomica.inicial(2L, List.of(
                new DefinicionAtributo("ancho_huella", TipoDato.NUMERO, true, true, List.of(), "cm"),
                new DefinicionAtributo("ruta", TipoDato.RUTA_GPS, false, true, List.of(), null)));
        RepositorioFichas fichas = new RepositorioFichas() {
            public List<Especie> especies() { return List.of(new Especie(2L, "Tremarctos ornatus", "Oso andino", true)); }
            public Optional<Especie> especie(long id) { return Optional.empty(); }
            public long crearEspecie(String a, String b, boolean c) { return 0; }
            public boolean existeNombreCientifico(String n) { return false; }
            public Optional<FichaTaxonomica> fichaVigente(long id) { return Optional.empty(); }
            public Optional<FichaTaxonomica> fichaEnVersion(long id, int v) {
                lecturasDeFicha++;
                return Optional.of(ficha);
            }
            public void guardarVersion(FichaTaxonomica f, long a) { }
        };
        return new ServicioInventario(inventario, fichas);
    }

    @Test
    void unaPaginaTiene50YDiceDondeSigue() {
        PaginaInventario pagina = servicioCon(120).pagina(SIN_FILTROS, null);

        assertThat(pagina.hallazgos()).hasSize(50);
        assertThat(pagina.siguiente()).isEqualTo(50L);
        assertThat(cursoresPedidos).containsExactly(0L);
    }

    @Test
    void recorrerTodasLasPaginasEntregaCadaHallazgoUnaSolaVez() {
        ServicioInventario servicio = servicioCon(120);
        List<Long> vistos = new ArrayList<>();
        Long cursor = null;
        do {
            PaginaInventario pagina = servicio.pagina(SIN_FILTROS, cursor);
            pagina.hallazgos().forEach(h -> vistos.add(h.id()));
            cursor = pagina.siguiente();
        } while (cursor != null);

        assertThat(vistos).hasSize(120).doesNotHaveDuplicates();
        assertThat(cursoresPedidos).containsExactly(0L, 50L, 100L);
    }

    @Test
    void conExactamente50NoHayPaginaSiguiente() {
        // Valor límite (S20): 50 cabe en una página, 51 no
        assertThat(servicioCon(50).pagina(SIN_FILTROS, null).siguiente()).isNull();
        assertThat(servicioCon(51).pagina(SIN_FILTROS, null).siguiente()).isEqualTo(50L);
    }

    @Test
    void losAtributosSalenFiltradosYLaFichaSeLeeUnaVezPorPagina() {
        PaginaInventario pagina = servicioCon(50).pagina(SIN_FILTROS, null);

        assertThat(pagina.hallazgos()).allSatisfy(h -> assertThat(h.atributos()).containsOnlyKeys("ancho_huella"));
        assertThat(lecturasDeFicha).isEqualTo(1);
    }

    @Test
    void sinHallazgosLaPaginaQuedaVacia() {
        PaginaInventario pagina = servicioCon(0).pagina(SIN_FILTROS, null);

        assertThat(pagina.hallazgos()).isEmpty();
        assertThat(pagina.siguiente()).isNull();
    }

    @Test
    void rechazaUnCursorNegativoYUnRangoDeFechasAlReves() {
        ServicioInventario servicio = servicioCon(10);

        assertThatThrownBy(() -> servicio.pagina(SIN_FILTROS, -1L)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> servicio.pagina(new ConsultaInventario(null, null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)), null))
                .isInstanceOf(ReglaDeNegocioException.class);
    }
}
