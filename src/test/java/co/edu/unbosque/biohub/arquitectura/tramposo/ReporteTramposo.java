package co.edu.unbosque.biohub.arquitectura.tramposo;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta;
import co.edu.unbosque.biohub.persistencia.coordenadas.LecturaCoordenadas;

/**
 * Clase "tramposa" SOLO PARA PRUEBAS: lee coordenadas sin pasar por la autorización.
 * Existe para demostrar que las reglas de SeguridadArquitecturaTest la atrapan.
 * No está en src/main: nunca llega a la aplicación.
 */
public class ReporteTramposo {

    private final LecturaCoordenadas lectura;

    public ReporteTramposo(LecturaCoordenadas lectura) {
        this.lectura = lectura;
    }

    public Optional<UbicacionExacta> ubicacionSinPermiso(long hallazgoId) {
        return lectura.deHallazgo(hallazgoId);
    }
}
