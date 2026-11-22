package co.edu.unbosque.biohub.dominio.equipos;

import java.time.Instant;

/**
 * Una entrada del historial de un equipo: quién lo recibió, quién lo asignó, cuándo y por qué.
 * Nunca se edita ni se borra (trazabilidad de los activos del Instituto, HU-03).
 */
public record AsignacionEquipo(long equipoId, long responsableId, String responsableNombre,
                               long asignadoPor, Instant asignadoEn, String motivo) {
}
