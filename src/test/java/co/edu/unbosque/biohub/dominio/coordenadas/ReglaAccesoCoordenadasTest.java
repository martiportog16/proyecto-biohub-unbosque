package co.edu.unbosque.biohub.dominio.coordenadas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.edu.unbosque.biohub.dominio.coordenadas.ReglaAccesoCoordenadas.Motivo;
import co.edu.unbosque.biohub.dominio.usuarios.Rol;

/**
 * PR-14 (RNF-06): la regla de acceso, aislada, para cada rol.
 * Sin base de datos, sin web, sin Spring: corre en milisegundos (S20: FIRST).
 */
class ReglaAccesoCoordenadasTest {

    /** La matriz completa de P-24: cada rol × especie en peligro / fuera de peligro. */
    @ParameterizedTest(name = "{0}, en peligro={1} -> {2}")
    @CsvSource({
            "ALTO_RANGO,               true,  ALTO_RANGO_ESPECIE_EN_PELIGRO",
            "ALTO_RANGO,               false, CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO",
            "INVESTIGADOR,             true,  ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO",
            "INVESTIGADOR,             false, CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO",
            "ADMINISTRADOR_CIENTIFICO, true,  ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO",
            "ADMINISTRADOR_CIENTIFICO, false, CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO",
            "DIRECTOR_CIENTIFICO,      true,  ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO",
            "DIRECTOR_CIENTIFICO,      false, CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO",
            "AUDITOR_CIENTIFICO,       true,  ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO",
            "AUDITOR_CIENTIFICO,       false, CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO",
            "ANALISTA,                 true,  ROL_NO_CIENTIFICO",
            "ANALISTA,                 false, ROL_NO_CIENTIFICO",
            "ADMINISTRADOR_PLATAFORMA, true,  ROL_NO_CIENTIFICO",
            "ADMINISTRADOR_PLATAFORMA, false, ROL_NO_CIENTIFICO",
    })
    void matrizDeRoles(Rol rol, boolean enPeligro, Motivo esperado) {
        assertThat(ReglaAccesoCoordenadas.evaluar(rol, true, enPeligro)).isEqualTo(esperado);
    }

    @Test
    void sinRolNuncaSePermite() {
        // Visitante o consumidor externo: no tienen rol
        assertThat(ReglaAccesoCoordenadas.evaluar(null, true, true).permite()).isFalse();
        assertThat(ReglaAccesoCoordenadas.evaluar(null, true, false).permite()).isFalse();
    }

    @Test
    void unUsuarioInactivoNoVeNadaAunqueSeaDeAltoRango() {
        assertThat(ReglaAccesoCoordenadas.evaluar(Rol.ALTO_RANGO, false, true)).isEqualTo(Motivo.USUARIO_INACTIVO);
        assertThat(ReglaAccesoCoordenadas.evaluar(Rol.INVESTIGADOR, false, false)).isEqualTo(Motivo.USUARIO_INACTIVO);
    }

    /**
     * Red de seguridad: si mañana alguien agrega un rol nuevo al enum, esta prueba
     * lo revisa sin que nadie tenga que acordarse de actualizar la matriz.
     */
    @Test
    void paraUnaEspecieEnPeligroElUnicoRolPermitidoEsAltoRango() {
        Set<Rol> permitidos = Arrays.stream(Rol.values())
                .filter(rol -> ReglaAccesoCoordenadas.evaluar(rol, true, true).permite())
                .collect(Collectors.toSet());

        assertThat(permitidos).containsExactly(Rol.ALTO_RANGO);
    }

    @Test
    void paraUnaEspecieFueraDePeligroSoloEntranLosRolesCientificos() {
        Set<Rol> permitidos = Arrays.stream(Rol.values())
                .filter(rol -> ReglaAccesoCoordenadas.evaluar(rol, true, false).permite())
                .collect(Collectors.toSet());

        assertThat(permitidos).isEqualTo(EnumSet.of(Rol.INVESTIGADOR, Rol.ADMINISTRADOR_CIENTIFICO,
                Rol.DIRECTOR_CIENTIFICO, Rol.AUDITOR_CIENTIFICO, Rol.ALTO_RANGO));
    }

    @Test
    void cadaMotivoDiceSiPermiteYTieneUnaExplicacion() {
        for (Motivo motivo : Motivo.values()) {
            assertThat(motivo.descripcion()).isNotBlank();
        }
        assertThat(Arrays.stream(Motivo.values()).filter(Motivo::permite))
                .containsExactly(Motivo.ALTO_RANGO_ESPECIE_EN_PELIGRO, Motivo.CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO);
    }
}
