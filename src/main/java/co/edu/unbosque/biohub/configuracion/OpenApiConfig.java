package co.edu.unbosque.biohub.configuracion;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Contrato OpenAPI (S16). En Swagger UI aparece el botón "Authorize" para pegar
 * el token que devuelve /api/cientifico/auth/login.
 *
 * El servidor es RELATIVO ("/"): Swagger UI manda las peticiones a la misma
 * dirección y puerto desde donde se abrió la página, sin importar el proxy.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI contratoBioHub() {
        return new OpenAPI()
                .info(new Info().title("BioHub API").version("v1")
                        .description("Registro y vigilancia de biodiversidad"))
                .servers(List.of(new Server().url("/").description("Este mismo servidor")))
                .components(new Components()
                        .addSecuritySchemes("token",
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        // API externa /v1: los consumidores se identifican con su API key (RF-13)
                        .addSecuritySchemes("apiKey",
                                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER).name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("token"));
    }
}
