package co.edu.unbosque.biohub.dominio.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Reglas para cambiar el límite de un consumidor (S20: valores límite). */
class ConsumidorTest {

    @Test
    void elLimiteDebeEstarEntre1Y10000() {
        assertThat(Consumidor.erroresDeCambio(0, null)).hasSize(1);
        assertThat(Consumidor.erroresDeCambio(1, null)).isEmpty();
        assertThat(Consumidor.erroresDeCambio(10_000, null)).isEmpty();
        assertThat(Consumidor.erroresDeCambio(10_001, null)).hasSize(1);
        assertThat(Consumidor.erroresDeCambio(-5, null)).hasSize(1);
    }

    @Test
    void hayQueEnviarAlgo() {
        assertThat(Consumidor.erroresDeCambio(null, null)).containsExactly("Envíe limitePorMinuto, activo o ambos");
        assertThat(Consumidor.erroresDeCambio(null, false)).isEmpty();
    }

    @Test
    void cambiarSoloLoQueSeEnvia() {
        Consumidor ong = new Consumidor(2, "ONG", 5, true);

        assertThat(ong.con(20, null)).isEqualTo(new Consumidor(2, "ONG", 20, true));
        assertThat(ong.con(null, false)).isEqualTo(new Consumidor(2, "ONG", 5, false));
    }
}
