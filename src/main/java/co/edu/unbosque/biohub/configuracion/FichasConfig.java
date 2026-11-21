package co.edu.unbosque.biohub.configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;

/**
 * El dominio no usa Spring. Aquí se arma el catálogo de estrategias de validación
 * y se entrega a la aplicación por inyección de dependencias (principio D).
 */
@Configuration
public class FichasConfig {

    @Bean
    CatalogoValidadores catalogoValidadores() {
        return CatalogoValidadores.estandar();
    }
}
