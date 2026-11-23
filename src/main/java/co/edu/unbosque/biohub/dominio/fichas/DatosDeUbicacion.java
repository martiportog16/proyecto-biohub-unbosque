package co.edu.unbosque.biohub.dominio.fichas;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Separa los valores de un hallazgo en "visibles" y "de ubicación" (RNF-06, RF-11).
 *
 * Son de ubicación:
 *   - RUTA_GPS: una lista de coordenadas. Mostrarla sería entregar la ubicación exacta
 *     por la puerta de atrás, sin pasar por la autorización.
 *   - ARCHIVO: una foto o una grabación puede traer la ubicación en sus metadatos.
 *
 * Los datos de ubicación solo salen por la autorización de coordenadas, que revisa el rol
 * y deja registro (AutorizacionCoordenadas). Un valor que la ficha no define tampoco sale:
 * lo que no se conoce, no se muestra.
 *
 * Se usa la ficha EN LA VERSIÓN con que se registró el hallazgo: un atributo retirado
 * después conserva su tipo.
 *
 * (La encontró la auditoría independiente: la ruta GPS de un oso andino, especie en
 * peligro, salía en GET /hallazgos para cualquier rol.)
 */
public final class DatosDeUbicacion {

    public static final Set<TipoDato> TIPOS_DE_UBICACION = EnumSet.of(TipoDato.RUTA_GPS, TipoDato.ARCHIVO);

    /** visibles: se pueden mostrar a quien vea el hallazgo. ubicacion: solo tras autorizar. */
    public record Separados(Map<String, Object> visibles, Map<String, Object> ubicacion) {

        public List<String> nombresProtegidos() {
            return List.copyOf(ubicacion.keySet());
        }
    }

    private DatosDeUbicacion() {
    }

    public static Separados separar(FichaTaxonomica ficha, Map<String, Object> valores) {
        Map<String, Object> visibles = new LinkedHashMap<>();
        Map<String, Object> ubicacion = new LinkedHashMap<>();
        for (DefinicionAtributo definicion : ficha.atributos()) {
            Object valor = valores.get(definicion.nombre());
            if (valor == null) {
                continue;
            }
            if (TIPOS_DE_UBICACION.contains(definicion.tipo())) {
                ubicacion.put(definicion.nombre(), valor);
            } else {
                visibles.put(definicion.nombre(), valor);
            }
        }
        return new Separados(Map.copyOf(visibles), Map.copyOf(ubicacion));
    }
}
