package co.edu.unbosque.biohub.aplicacion.fichas;

import java.util.List;

import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;

/**
 * Datos que la aplicación entrega a la presentación y recibe de ella.
 * Así la presentación no conoce las clases del dominio (capas cerradas, ADR-02).
 */
public final class VistasFichas {

    private VistasFichas() {
    }

    public record VistaEspecie(long id, String nombreCientifico, String nombreComun, boolean enPeligro) {
        static VistaEspecie de(Especie e) {
            return new VistaEspecie(e.id(), e.nombreCientifico(), e.nombreComun(), e.enPeligro());
        }
    }

    public record VistaAtributo(String nombre, String tipo, boolean obligatorio, boolean activo,
                                List<String> opciones, String unidad) {
        static VistaAtributo de(DefinicionAtributo a) {
            return new VistaAtributo(a.nombre(), a.tipo().name(), a.obligatorio(), a.activo(), a.opciones(), a.unidad());
        }
    }

    public record VistaFicha(long especieId, int version, List<VistaAtributo> atributos) {
        static VistaFicha de(FichaTaxonomica f) {
            return new VistaFicha(f.especieId(), f.version(), f.atributos().stream().map(VistaAtributo::de).toList());
        }
    }

    /** Lo que llega para definir un atributo nuevo. "tipo" llega como texto: TEXTO, NUMERO, ... */
    public record NuevoAtributo(String nombre, String tipo, boolean obligatorio, List<String> opciones, String unidad) {
    }

    public record NuevaEspecie(String nombreCientifico, String nombreComun, boolean enPeligro,
                               List<NuevoAtributo> atributos) {
    }
}
