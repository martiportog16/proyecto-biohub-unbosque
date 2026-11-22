package co.edu.unbosque.biohub.aplicacion.fichas;

import java.util.List;

import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevoAtributo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.VistaAtributo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.GrupoTaxonomico;
import co.edu.unbosque.biohub.dominio.fichas.MembresiaGrupo;

/** Lo que entra y sale de los grupos taxonómicos hacia la presentación. */
public final class VistasGrupos {

    private VistasGrupos() {
    }

    public record VistaMiembro(long especieId, boolean heredaFicha) {
        static VistaMiembro de(MembresiaGrupo m) {
            return new VistaMiembro(m.especieId(), m.heredaFicha());
        }
    }

    /** @param versionFicha null si el grupo todavía no tiene ficha */
    public record VistaGrupo(long id, String nombre, String descripcion, Integer versionFicha,
                             List<VistaMiembro> especies) {
        static VistaGrupo de(GrupoTaxonomico g, FichaTaxonomica ficha, List<MembresiaGrupo> miembros) {
            return new VistaGrupo(g.id(), g.nombre(), g.descripcion(), ficha == null ? null : ficha.version(),
                    miembros.stream().map(VistaMiembro::de).toList());
        }
    }

    public record VistaFichaGrupo(long grupoId, int version, List<VistaAtributo> atributos) {
        static VistaFichaGrupo de(FichaTaxonomica f) {
            return new VistaFichaGrupo(f.especieId(), f.version(), f.atributos().stream().map(VistaAtributo::de).toList());
        }
    }

    /** Resultado de un cambio en la ficha del grupo: la versión nueva y a qué especies se copió. */
    public record CambioDeGrupo(VistaFichaGrupo ficha, List<Long> especiesActualizadas) {
    }

    public record NuevoGrupo(String nombre, String descripcion, List<NuevoAtributo> atributos) {
    }

    public record Asignacion(long especieId, boolean heredarFicha) {
    }
}
