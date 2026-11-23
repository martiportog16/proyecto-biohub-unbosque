package co.edu.unbosque.biohub.dominio.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Quién puede administrar a quién (P-28, S-01). Lógica pura: sin base de datos ni Spring (S20).
 */
class ReglasGestionUsuariosTest {

    private static final Usuario PLATAFORMA = new Usuario(1L, "p@b.co", "Sofía", "", Rol.ADMINISTRADOR_PLATAFORMA, true);
    private static final Usuario DIRECTORA = new Usuario(2L, "d@b.co", "Marta", "", Rol.DIRECTOR_CIENTIFICO, true);
    private static final Usuario ANA = new Usuario(3L, "a@b.co", "Ana", "", Rol.INVESTIGADOR, true);
    private static final Usuario ELENA = new Usuario(4L, "e@b.co", "Elena", "", Rol.ALTO_RANGO, true);
    private static final Usuario PEDRO = new Usuario(5L, "pe@b.co", "Pedro", "", Rol.ANALISTA, true);
    private static final Usuario INACTIVO = new Usuario(6L, "i@b.co", "Luis", "", Rol.INVESTIGADOR, false);

    /* ---------- Crear ---------- */

    @Test
    void soloLaPlataformaCreaUsuariosYNuncaDeAltoRango() {
        assertThat(ReglasGestionUsuarios.permisoParaCrear(Rol.ADMINISTRADOR_PLATAFORMA, Rol.INVESTIGADOR)).isEmpty();
        assertThat(ReglasGestionUsuarios.permisoParaCrear(Rol.ADMINISTRADOR_PLATAFORMA, Rol.ALTO_RANGO))
                .hasValueSatisfying(m -> assertThat(m).contains("director científico"));
        assertThat(ReglasGestionUsuarios.permisoParaCrear(Rol.DIRECTOR_CIENTIFICO, Rol.INVESTIGADOR)).isPresent();
    }

    @Test
    void elAltaReuneTodosLosErroresDeUnaVez() {
        assertThat(ReglasGestionUsuarios.erroresDeAlta("sin-arroba", " ", null, "x"))
                .containsExactly("correo: no es un correo válido", "nombre: es obligatorio", "rol: es obligatorio",
                        "motivo: es obligatorio (al menos 5 caracteres)");
        assertThat(ReglasGestionUsuarios.erroresDeAlta("nueva@biohub.co", "Nueva", Rol.INVESTIGADOR, "Ingresa al proyecto")).isEmpty();
    }

    /* ---------- Cambiar el rol ---------- */

    @Test
    void elAltoRangoLoDaYLoQuitaSoloUnDirector() {   // S-01
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(DIRECTORA, ANA, Rol.ALTO_RANGO)).isEmpty();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(DIRECTORA, ELENA, Rol.INVESTIGADOR)).isEmpty();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(PLATAFORMA, ANA, Rol.ALTO_RANGO)).isPresent();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(PLATAFORMA, ELENA, Rol.INVESTIGADOR)).isPresent();
    }

    @Test
    void losDemasRolesLosCambiaSoloLaPlataforma() {
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(PLATAFORMA, ANA, Rol.ANALISTA)).isEmpty();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(DIRECTORA, ANA, Rol.AUDITOR_CIENTIFICO))
                .hasValueSatisfying(m -> assertThat(m).contains("solo da o quita el alto rango"));
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void nadieSeCambiaSuPropioRol(Rol rolNuevo) {
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(DIRECTORA, DIRECTORA, rolNuevo)).isPresent();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarRol(PLATAFORMA, PLATAFORMA, rolNuevo)).isPresent();
    }

    @Test
    void elAltoRangoEsSoloParaElPersonalCientifico() {
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeRol(PEDRO, Rol.ALTO_RANGO, "Lo pidió la dirección"))
                .containsExactly("rol: solo el personal científico puede ser de alto rango");
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeRol(ELENA, Rol.ANALISTA, "Cambia de área"))
                .containsExactly("rol: al quitar el alto rango, escoja un rol científico");
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeRol(ANA, Rol.ALTO_RANGO, "Lidera la expedición al Chocó")).isEmpty();
    }

    @Test
    void noSeCambiaElRolDeUnInactivoNiAlMismoRolNiSinMotivo() {
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeRol(INACTIVO, Rol.ANALISTA, "Cambio de área"))
                .containsExactly("usuario: Luis está inactivo; reactívelo primero");
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeRol(ANA, Rol.INVESTIGADOR, null))
                .containsExactly("rol: Ana ya tiene ese rol", "motivo: es obligatorio (al menos 5 caracteres)");
    }

    /* ---------- Activar y desactivar ---------- */

    @Test
    void soloLaPlataformaDesactivaYNoASiMisma() {
        assertThat(ReglasGestionUsuarios.permisoParaCambiarEstado(PLATAFORMA, ELENA)).isEmpty();
        assertThat(ReglasGestionUsuarios.permisoParaCambiarEstado(PLATAFORMA, PLATAFORMA))
                .hasValue("Nadie puede desactivarse a sí mismo");
        assertThat(ReglasGestionUsuarios.permisoParaCambiarEstado(DIRECTORA, ANA)).isPresent();
        assertThat(ReglasGestionUsuarios.erroresDeCambioDeEstado(INACTIVO, false, "Ya se fue"))
                .containsExactly("activo: Luis ya está inactivo");
    }

    /* ---------- Clave ---------- */

    @Test
    void laClaveNuevaTieneAlMenos10CaracteresYEsDistinta() {   // valores límite (S20)
        assertThat(ReglasGestionUsuarios.erroresDeClaveNueva("123456789", "vieja")).hasSize(1);
        assertThat(ReglasGestionUsuarios.erroresDeClaveNueva("1234567890", "vieja")).isEmpty();
        assertThat(ReglasGestionUsuarios.erroresDeClaveNueva("misma-clave-1", "misma-clave-1"))
                .containsExactly("claveNueva: debe ser distinta de la actual");
    }
}
