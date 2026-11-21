package co.edu.unbosque.biohub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de BioHub.
 *
 * Es UN solo artefacto (ADR-01). El perfil de Spring decide qué rol cumple esta instancia:
 *   - "cientifico": registra y reclasifica; único que escribe en la base primaria.
 *   - "publico":    portal público y API externa /v1; lee de caché y réplica.
 *   - "batch":      genera el informe mensual leyendo la réplica de analítica.
 */
@SpringBootApplication
public class BioHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioHubApplication.class, args);
    }
}
