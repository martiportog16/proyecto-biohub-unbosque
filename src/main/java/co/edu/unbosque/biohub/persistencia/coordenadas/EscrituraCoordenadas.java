package co.edu.unbosque.biohub.persistencia.coordenadas;

import co.edu.unbosque.biohub.dominio.hallazgos.Coordenada;

/**
 * IEscrituraCoordenadas del diseño (C-3, aprobado).
 *
 * Solo ESCRIBE: la usa el registro de hallazgos. No tiene ningún método de lectura,
 * así que quien la recibe no puede leer coordenadas. La lectura vive en otra interfaz
 * (LecturaCoordenadas, paquete 07) cuyo único cliente es la autorización (RNF-06).
 */
public interface EscrituraCoordenadas {

    void guardar(long hallazgoId, Coordenada coordenada);
}
