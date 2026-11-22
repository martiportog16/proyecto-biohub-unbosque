package co.edu.unbosque.biohub.aplicacion.analitica;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/** Datos de entrada y salida de los informes de presión (capas cerradas, ADR-02). */
public final class VistasInformes {

    private VistasInformes() {
    }

    public record VistaResumenInforme(YearMonth periodo, Instant generadoEn, int totalDepartamentos) {
    }

    /** valor y base son null cuando el departamento quedó "sin datos". */
    public record VistaIndice(String departamento, Double valor, int especiesEnElMes, Double base, boolean sinDatos) {
    }

    public record VistaInforme(YearMonth periodo, Instant generadoEn, int totalDepartamentos,
                               List<VistaIndice> indices) {
    }

    /** Lo que envía el analista: el mes en formato AAAA-MM. */
    public record NuevaSolicitud(YearMonth periodo) {
    }

    /**
     * PENDIENTE: el batch todavía no la toma. ATENDIDA: el informe ya se puede consultar.
     * SIN_DATOS: el batch la revisó pero no había hallazgos; se puede volver a pedir después.
     * INFORME_YA_EXISTE: no hizo falta crear la solicitud.
     */
    public enum EstadoSolicitud { PENDIENTE, ATENDIDA, SIN_DATOS, INFORME_YA_EXISTE }

    /** id es null cuando no hizo falta crear la solicitud porque el informe ya existía. */
    public record VistaSolicitud(Long id, YearMonth periodo, EstadoSolicitud estado, Instant solicitadoEn,
                                 Instant atendidaEn) {
    }
}
