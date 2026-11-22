package co.edu.unbosque.biohub.dominio.equipos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EquipoCampoTest {

    @Test
    void ningunEquipoExisteSinResponsable() {   // HU-03, criterio 4
        assertThatThrownBy(() -> EquipoCampo.crear("DRON-01", "DRON", null, null))
                .isInstanceOf(ReglaDeEquipoException.class)
                .extracting(e -> ((ReglaDeEquipoException) e).getErrores())
                .asList().containsExactly("responsableId: un equipo no se puede registrar sin responsable");
    }

    @Test
    void normalizaElCodigoYElTipo() {
        EquipoCampo e = EquipoCampo.crear("  dron-007 ", "camara_trampa", "  ", 4L);

        assertThat(e.codigo()).isEqualTo("DRON-007");
        assertThat(e.tipo()).isEqualTo(TipoEquipo.CAMARA_TRAMPA);
        assertThat(e.descripcion()).isNull();
        assertThat(e.id()).isNull();
    }

    @Test
    void reportaTodosLosErroresJuntos() {
        assertThatThrownBy(() -> EquipoCampo.crear("x", "satelite", null, null))
                .extracting(e -> ((ReglaDeEquipoException) e).getErrores())
                .asList().hasSize(3);
    }

    @Test
    void elCodigoSoloAdmiteLetrasNumerosYGuiones() {
        assertThatThrownBy(() -> EquipoCampo.crear("DRON 01;", "DRON", null, 1L))
                .isInstanceOf(ReglaDeEquipoException.class);
    }

    @Test
    void reasignarExigeOtroResponsableYMotivo() {
        EquipoCampo e = EquipoCampo.crear("GPS-1", "GPS", null, 1L).conId(9);

        assertThatThrownBy(() -> e.reasignarA(1L, "salida de campo"))
                .extracting(x -> ((ReglaDeEquipoException) x).getErrores())
                .asList().containsExactly("responsableId: el equipo ya está a cargo de esa persona");
        assertThatThrownBy(() -> e.reasignarA(null, "ok"))
                .extracting(x -> ((ReglaDeEquipoException) x).getErrores())
                .asList().hasSize(2);

        EquipoCampo reasignado = e.reasignarA(2L, "Ana sale al Meta");
        assertThat(reasignado.responsableId()).isEqualTo(2L);
        assertThat(reasignado.id()).isEqualTo(9L);
        assertThat(e.responsableId()).isEqualTo(1L);   // inmutable: el original no cambia
    }
}
