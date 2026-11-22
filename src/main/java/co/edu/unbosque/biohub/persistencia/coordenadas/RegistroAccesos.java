package co.edu.unbosque.biohub.persistencia.coordenadas;

import java.util.List;

import co.edu.unbosque.biohub.dominio.coordenadas.AccesoCoordenadas;

/**
 * El registro de accesos a coordenadas (P-18). Solo agrega y lee:
 * no tiene métodos para editar ni borrar.
 */
public interface RegistroAccesos {

    void registrar(AccesoCoordenadas acceso);

    /** Los accesos más recientes primero. Con soloNegados = true, solo los intentos rechazados. */
    List<AccesoCoordenadas> recientes(boolean soloNegados, int limite);
}
