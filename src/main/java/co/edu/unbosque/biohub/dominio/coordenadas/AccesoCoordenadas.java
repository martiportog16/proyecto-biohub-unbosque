package co.edu.unbosque.biohub.dominio.coordenadas;

import java.time.Instant;

/**
 * Una consulta de coordenadas exactas, permitida o negada (P-18).
 *
 * Guarda quién, con qué rol, qué pidió, cuándo y qué se decidió. Con esto se
 * demuestra "0 accesos indebidos" (HU-08, criterio 2).
 *
 * @param hallazgoId null cuando se pidió el mapa de toda la especie
 * @param rol        el rol que tenía el usuario en ese momento
 */
public record AccesoCoordenadas(Long id, long usuarioId, String rol, long especieId, Long hallazgoId,
                                boolean especieEnPeligro, boolean permitido, String motivo, Instant ocurridoEn) {
}
