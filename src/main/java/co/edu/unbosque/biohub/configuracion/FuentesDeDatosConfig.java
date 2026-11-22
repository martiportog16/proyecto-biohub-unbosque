package co.edu.unbosque.biohub.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

import co.edu.unbosque.biohub.persistencia.comun.ConexionPrimaria;

/**
 * Decide a qué base apunta ConexionPrimaria según el rol (P-03).
 *
 *   científico / público: la conexión normal (spring.datasource).
 *   batch:                una segunda conexión, solo para guardar el informe.
 *
 * En el batch, spring.datasource apunta a la RÉPLICA de analítica y es de solo lectura.
 */
@Configuration
public class FuentesDeDatosConfig {

    @Bean
    @Profile("!batch")
    ConexionPrimaria conexionPrimaria(JdbcClient jdbc, PlatformTransactionManager transacciones) {
        return new ConexionPrimaria(jdbc, new TransactionTemplate(transacciones), null);
    }

    /**
     * El grupo de conexiones a la primaria NO se registra como un DataSource de Spring
     * a propósito: si hubiera dos, Spring Boot dejaría de configurar solo el principal.
     * Es pequeño (2 conexiones): el batch guarda unas pocas filas al mes.
     * Se conecta la primera vez que se usa, así el batch arranca aunque la primaria esté caída.
     */
    @Bean
    @Profile("batch")
    ConexionPrimaria conexionPrimariaDelBatch(@Value("${biohub.batch.primaria.url}") String url,
                                              @Value("${biohub.batch.primaria.usuario}") String usuario,
                                              @Value("${biohub.batch.primaria.clave}") String clave) {
        HikariDataSource primaria = new HikariDataSource();
        primaria.setPoolName("primaria-batch");
        primaria.setJdbcUrl(url);
        primaria.setUsername(usuario);
        primaria.setPassword(clave);
        primaria.setMaximumPoolSize(2);
        primaria.setMinimumIdle(0);
        return new ConexionPrimaria(JdbcClient.create(primaria),
                new TransactionTemplate(new DataSourceTransactionManager(primaria)), primaria);
    }
}
