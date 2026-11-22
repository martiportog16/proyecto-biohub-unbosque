package co.edu.unbosque.biohub.aplicacion.integracion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.dominio.integracion.Consumidor;
import co.edu.unbosque.biohub.persistencia.integracion.ContadorPeticiones;
import co.edu.unbosque.biohub.persistencia.integracion.RepositorioConsumidores;

/**
 * Decide si un consumidor externo puede hacer una petición más (RF-13, T-19, P-10).
 *
 * - Sin API key, con una desconocida o de un consumidor inactivo: no autorizado (401).
 * - Si ya hizo su límite de peticiones en este minuto: excedido (429), con los segundos
 *   que faltan para el siguiente minuto (Retry-After).
 * - Un consumidor que se pasa NO afecta a los demás: cada uno tiene su propia cuenta.
 */
@Service
public class ServicioLimitePeticiones {

    private final RepositorioConsumidores consumidores;
    private final ContadorPeticiones contador;
    private final Clock reloj;

    public ServicioLimitePeticiones(RepositorioConsumidores consumidores, ContadorPeticiones contador, Clock reloj) {
        this.consumidores = consumidores;
        this.contador = contador;
        this.reloj = reloj;
    }

    public sealed interface Decision permits NoAutorizado, Excedido, Permitido {
    }

    public record NoAutorizado() implements Decision {
    }

    public record Excedido(String consumidor, int limite, long segundosParaReintentar) implements Decision {
    }

    public record Permitido(long consumidorId, String consumidor, int limite, long restantes) implements Decision {
    }

    public Decision verificar(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new NoAutorizado();
        }
        Optional<Consumidor> encontrado = consumidores.buscarPorHuella(huella(apiKey)).filter(Consumidor::activo);
        if (encontrado.isEmpty()) {
            return new NoAutorizado();
        }
        Consumidor c = encontrado.get();

        long segundoEpoch = reloj.instant().getEpochSecond();
        long minuto = segundoEpoch / 60;
        long usadas = contador.incrementar(c.id(), minuto);

        if (usadas > c.limitePorMinuto()) {
            long faltan = 60 - (segundoEpoch % 60);
            return new Excedido(c.nombre(), c.limitePorMinuto(), faltan);
        }
        return new Permitido(c.id(), c.nombre(), c.limitePorMinuto(), c.limitePorMinuto() - usadas);
    }

    /** De la API key solo se guarda su huella SHA-256: si alguien roba la base, no obtiene las llaves. */
    static String huella(String apiKey) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
