package co.edu.unbosque.biohub.configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad BASE.
 *
 * En este primer paquete todo queda abierto para poder arrancar el proyecto.
 * El paquete de usuarios y login (Martin, P-28) reemplaza esta clase por la
 * versión con JWT y permisos por rol.
 */
@Configuration
@ConditionalOnWebApplication   // el rol batch no atiende HTTP, así que no necesita esta cadena
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
