package co.edu.unbosque.biohub.aplicacion.analitica;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.analitica.CalculadoraIndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.ConteoMensual;
import co.edu.unbosque.biohub.dominio.analitica.IndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.dominio.analitica.ZonasDelPeriodo;
import co.edu.unbosque.biohub.persistencia.analitica.LecturaAnalitica;
import co.edu.unbosque.biohub.persistencia.analitica.RepositorioInformes;

/**
 * Genera el informe de presión de un mes (HU-11, secuencia del batch).
 *
 *   1. Si el informe de ese mes ya existe, no hace nada (se puede repetir sin riesgo).
 *   2. LEE los conteos de la réplica de analítica: la parte pesada (RNF-09).
 *      Si no hay ningún hallazgo en esos meses, NO guarda nada: un informe no se
 *      puede corregir después, y uno vacío quedaría vacío para siempre.
 *   3. Calcula el índice de cada departamento con la fórmula P-26 (lógica pura).
 *   4. GUARDA el resultado en la primaria, en una sola transacción (C-1).
 *
 * Entre el paso 2 y el 4 no hay ninguna transacción abierta en la primaria:
 * el registro de hallazgos no espera nada del batch.
 */
@Service
public class GeneradorInforme {

    public enum Resultado { GENERADO, YA_EXISTIA, SIN_DATOS }

    private static final Logger LOG = LoggerFactory.getLogger(GeneradorInforme.class);

    private final LecturaAnalitica lectura;
    private final RepositorioInformes informes;
    private final Clock reloj;
    private final CalculadoraIndicePresion calculadora = new CalculadoraIndicePresion();

    public GeneradorInforme(LecturaAnalitica lectura, RepositorioInformes informes, Clock reloj) {
        this.lectura = lectura;
        this.informes = informes;
        this.reloj = reloj;
    }

    public Resultado generar(YearMonth periodo) {
        if (!CalculadoraIndicePresion.sePuedeGenerar(periodo, LocalDate.now(reloj))) {
            throw new ReglaDeNegocioException("El informe no se puede generar",
                    List.of("periodo: solo se generan informes de meses que ya terminaron"));
        }
        if (informes.existe(periodo)) {
            return Resultado.YA_EXISTIA;
        }

        Instant inicio = Instant.now(reloj);
        List<ConteoMensual> conteos = lectura.especiesPorDepartamentoYMes(
                ZonasDelPeriodo.primerMesNecesario(periodo), periodo);
        if (conteos.isEmpty()) {
            LOG.info("Sin hallazgos entre {} y {}: el informe de {} no se genera todavía",
                    ZonasDelPeriodo.primerMesNecesario(periodo), periodo, periodo);
            return Resultado.SIN_DATOS;
        }
        List<IndicePresion> indices = calculadora.calcularTodas(ZonasDelPeriodo.armar(periodo, conteos));

        try {
            informes.guardar(new InformeMensual(periodo, Instant.now(reloj), indices));
        } catch (DuplicateKeyException e) {
            // Otro proceso lo guardó mientras este calculaba: el resultado es el mismo.
            return Resultado.YA_EXISTIA;
        }
        LOG.info("Informe de {} generado: {} departamentos en {} ms", periodo, indices.size(),
                Duration.between(inicio, Instant.now(reloj)).toMillis());
        return Resultado.GENERADO;
    }
}
