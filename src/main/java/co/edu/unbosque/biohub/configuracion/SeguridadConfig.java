package co.edu.unbosque.biohub.configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quién puede entrar a qué (P-27).
 *
 * - Rutas públicas (/api/publico, /api/v1): sin login. Los externos se identifican
 *   con API key en los paquetes de integración externa (09a y 09b).
 * - Rutas científicas (/api/cientifico): exigen token JWT.
 * - Los permisos por rol se ponen en cada endpoint con @PreAuthorize.
 * - Las coordenadas exactas NO se deciden aquí: pasan por el componente central
 *   de autorización (RNF-06, paquete 07).
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication   // el rol batch no atiende HTTP, así que no necesita esta cadena
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/publico/**", "/api/v1/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/cientifico/auth/login").permitAll()
                .requestMatchers("/api/cientifico/estado").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles())));
        return http.build();
    }

    /** El claim "rol" del token se convierte en la autoridad ROLE_<rol>, que usa @PreAuthorize. */
    private JwtAuthenticationConverter convertidorDeRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("rol");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }
}
