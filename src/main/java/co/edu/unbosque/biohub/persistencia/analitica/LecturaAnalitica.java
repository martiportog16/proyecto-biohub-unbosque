package co.edu.unbosque.biohub.persistencia.analitica;

import java.time.YearMonth;
import java.util.List;

import co.edu.unbosque.biohub.dominio.analitica.ConteoMensual;

/**
 * La consulta PESADA del informe (RNF-09): recorre los hallazgos de varios meses.
 * En el batch se ejecuta contra la réplica de analítica, nunca contra la primaria (P-03).
 */
public interface LecturaAnalitica {

    /** Especies distintas por departamento y mes, entre los dos meses (ambos incluidos). */
    List<ConteoMensual> especiesPorDepartamentoYMes(YearMonth desde, YearMonth hasta);
}
