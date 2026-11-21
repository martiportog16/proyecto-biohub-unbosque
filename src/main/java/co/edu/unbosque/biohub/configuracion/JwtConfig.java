package co.edu.unbosque.biohub.configuracion;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import co.edu.unbosque.biohub.aplicacion.autenticacion.EmisorDeTokens;

/**
 * Tokens JWT firmados con HS256 (P-28).
 * El secreto viene de la variable JWT_SECRETO y debe ser IGUAL en todas las instancias,
 * para que un token emitido por una instancia científica sea válido en la otra.
 */
@Configuration
public class JwtConfig {

    private final SecretKey llave;
    private final Duration duracion;

    public JwtConfig(@Value("${biohub.jwt.secreto}") String secreto,
                     @Value("${biohub.jwt.duracion-minutos:60}") long minutos) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRETO debe tener al menos 32 caracteres");
        }
        this.llave = new SecretKeySpec(bytes, "HmacSHA256");
        this.duracion = Duration.ofMinutes(minutos);
    }

    @Bean
    PasswordEncoder codificadorDeClaves() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder decodificadorJwt() {
        return NimbusJwtDecoder.withSecretKey(llave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder codificadorJwt() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(llave));
    }

    @Bean
    EmisorDeTokens emisorDeTokens(JwtEncoder codificador) {
        return usuario -> {
            Instant ahora = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("biohub")
                    .subject(String.valueOf(usuario.id()))
                    .issuedAt(ahora)
                    .expiresAt(ahora.plus(duracion))
                    .claim("correo", usuario.correo())
                    .claim("nombre", usuario.nombre())
                    .claim("rol", usuario.rol().name())
                    .build();
            JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
            String valor = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
            return new EmisorDeTokens.TokenEmitido(valor, duracion.toSeconds());
        };
    }
}
