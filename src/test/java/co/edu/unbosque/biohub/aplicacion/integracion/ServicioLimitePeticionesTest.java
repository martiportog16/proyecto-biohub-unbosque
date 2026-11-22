package co.edu.unbosque.biohub.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Excedido;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.NoAutorizado;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Permitido;
import co.edu.unbosque.biohub.dominio.integracion.Consumidor;
import co.edu.unbosque.biohub.persistencia.integracion.ContadorPeticiones;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioConsumidores;

/**
 * Límite por consumidor (PR-11 en unidad), sin Redis ni base de datos:
 * - El contador es un FAKE en memoria.
 * - El reloj es un STUB: siempre son las 10:00:30, así faltan 30 s para el siguiente minuto.
 */
class ServicioLimitePeticionesTest {

    private static final Instant DIEZ_Y_MEDIO = Instant.parse("2026-11-20T10:00:30Z");

    private final Map<String, Long> cuentas = new HashMap<>();
    private final ContadorPeticiones contadorFake = (id, minuto) -> cuentas.merge(id + ":" + minuto, 1L, Long::sum);

    private final RepositorioConsumidores consumidoresFake = huella -> {
        if (huella.equals(ServicioLimitePeticiones.huella("llave-ong"))) {
            return Optional.of(new Consumidor(1, "ONG", 5, true));
        }
        if (huella.equals(ServicioLimitePeticiones.huella("llave-unal"))) {
            return Optional.of(new Consumidor(2, "UNAL", 60, true));
        }
        if (huella.equals(ServicioLimitePeticiones.huella("llave-vieja"))) {
            return Optional.of(new Consumidor(3, "Inactivo", 60, false));
        }
        return Optional.empty();
    };

    private ServicioLimitePeticiones servicioALas(Instant momento) {
        return new ServicioLimitePeticiones(consumidoresFake, contadorFake, Clock.fixed(momento, ZoneOffset.UTC));
    }

    @Test
    void sinLlaveConLlaveDesconocidaOInactivaNoAutoriza() {
        ServicioLimitePeticiones servicio = servicioALas(DIEZ_Y_MEDIO);
        assertThat(servicio.verificar(null)).isInstanceOf(NoAutorizado.class);
        assertThat(servicio.verificar("inventada")).isInstanceOf(NoAutorizado.class);
        assertThat(servicio.verificar("llave-vieja")).isInstanceOf(NoAutorizado.class);
    }

    @Test
    void laPeticionNumero5PasaYLa6SeRechazaConRetryAfter() {
        ServicioLimitePeticiones servicio = servicioALas(DIEZ_Y_MEDIO);
        for (int i = 1; i <= 4; i++) {
            servicio.verificar("llave-ong");
        }

        assertThat(servicio.verificar("llave-ong"))                       // la 5: justo en el límite
                .isInstanceOfSatisfying(Permitido.class, p -> assertThat(p.restantes()).isZero());
        assertThat(servicio.verificar("llave-ong"))                       // la 6: se pasa
                .isInstanceOfSatisfying(Excedido.class, e -> assertThat(e.segundosParaReintentar()).isEqualTo(30));
    }

    @Test
    void unConsumidorQueSePasaNoAfectaAOtro() {
        ServicioLimitePeticiones servicio = servicioALas(DIEZ_Y_MEDIO);
        for (int i = 1; i <= 10; i++) {
            servicio.verificar("llave-ong");
        }
        assertThat(servicio.verificar("llave-unal")).isInstanceOf(Permitido.class);
    }

    @Test
    void enElMinutoSiguienteLaCuentaVuelveAEmpezar() {
        ServicioLimitePeticiones ahora = servicioALas(DIEZ_Y_MEDIO);
        for (int i = 1; i <= 6; i++) {
            ahora.verificar("llave-ong");
        }
        ServicioLimitePeticiones unMinutoDespues = servicioALas(DIEZ_Y_MEDIO.plusSeconds(60));

        assertThat(unMinutoDespues.verificar("llave-ong")).isInstanceOf(Permitido.class);
    }

    @Test
    void laBaseGuardaLaHuellaNoLaLlave() {
        assertThat(ServicioLimitePeticiones.huella("demo-ong-2026"))
                .isEqualTo("f1d02d9b5ba72148eb641d399ef4e35fae3da239709bb6bcf73ee5ae403801bb");
    }
}
