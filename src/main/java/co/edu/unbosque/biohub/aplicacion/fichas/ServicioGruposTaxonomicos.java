package co.edu.unbosque.biohub.aplicacion.fichas;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.InvalidadorCachePublica;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevoAtributo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.Asignacion;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.CambioDeGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.NuevoGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.VistaFichaGrupo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasGrupos.VistaGrupo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.GrupoTaxonomico;
import co.edu.unbosque.biohub.dominio.fichas.MembresiaGrupo;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioGrupos;

/**
 * Fichas por grupo taxonómico (RF-02, HU-02 criterios 1 y 4).
 *
 * El grupo tiene su propia ficha versionada. Cuando cambia, se COPIA como versión nueva
 * a cada especie del grupo que la hereda. Las especies de otros grupos, y las que se
 * independizaron, no se tocan. Los hallazgos viejos conservan la versión con que se
 * registraron, igual que con un cambio hecho a una especie (PR-01).
 */
@Service
public class ServicioGruposTaxonomicos {

    private final RepositorioGrupos grupos;
    private final RepositorioFichas fichas;
    private final InvalidadorCachePublica cachePublica;

    public ServicioGruposTaxonomicos(RepositorioGrupos grupos, RepositorioFichas fichas,
                                     InvalidadorCachePublica cachePublica) {
        this.grupos = grupos;
        this.fichas = fichas;
        this.cachePublica = cachePublica;
    }

    public List<VistaGrupo> grupos() {
        return grupos.grupos().stream().map(this::vista).toList();
    }

    public VistaGrupo grupo(long grupoId) {
        return vista(existente(grupoId));
    }

    public VistaFichaGrupo fichaDelGrupo(long grupoId) {
        existente(grupoId);
        return grupos.fichaVigenteDelGrupo(grupoId).map(VistaFichaGrupo::de)
                .orElseThrow(() -> new NoEncontradoException("El grupo " + grupoId + " todavía no tiene ficha"));
    }

    @Transactional
    public VistaGrupo crear(NuevoGrupo datos, long autorId) {
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            throw new ReglaDeNegocioException("El grupo no es válido", List.of("nombre: es obligatorio"));
        }
        if (grupos.existeNombre(datos.nombre().trim())) {
            throw new ReglaDeNegocioException("El grupo no es válido",
                    List.of("nombre: ya existe '" + datos.nombre().trim() + "'"));
        }
        long id = grupos.crear(datos.nombre().trim(), datos.descripcion());
        List<NuevoAtributo> atributos = datos.atributos() == null ? List.of() : datos.atributos();
        if (!atributos.isEmpty()) {
            FichaTaxonomica ficha = ServicioFichas.traducir(() ->
                    FichaTaxonomica.inicial(id, atributos.stream().map(ServicioFichas::aDefinicion).toList()));
            grupos.guardarVersionDelGrupo(ficha, autorId);
        }
        return grupo(id);
    }

    @Transactional
    public CambioDeGrupo agregarAtributo(long grupoId, NuevoAtributo atributo, long autorId) {
        existente(grupoId);
        FichaTaxonomica nueva = ServicioFichas.traducir(() -> grupos.fichaVigenteDelGrupo(grupoId)
                .map(f -> f.conAtributo(ServicioFichas.aDefinicion(atributo)))
                .orElseGet(() -> FichaTaxonomica.inicial(grupoId, List.of(ServicioFichas.aDefinicion(atributo)))));
        grupos.guardarVersionDelGrupo(nueva, autorId);
        return new CambioDeGrupo(VistaFichaGrupo.de(nueva), propagar(grupoId, nueva, autorId));
    }

    @Transactional
    public CambioDeGrupo retirarAtributo(long grupoId, String nombre, long autorId) {
        existente(grupoId);
        FichaTaxonomica actual = grupos.fichaVigenteDelGrupo(grupoId)
                .orElseThrow(() -> new NoEncontradoException("El grupo " + grupoId + " todavía no tiene ficha"));
        FichaTaxonomica nueva = ServicioFichas.traducir(() -> actual.sinAtributo(nombre));
        grupos.guardarVersionDelGrupo(nueva, autorId);
        return new CambioDeGrupo(VistaFichaGrupo.de(nueva), propagar(grupoId, nueva, autorId));
    }

    /**
     * Mete una especie al grupo. Si hereda y el grupo ya tiene ficha, la recibe de una vez.
     * Una especie solo pertenece a un grupo: asignarla aquí la saca del anterior.
     */
    @Transactional
    public VistaGrupo asignar(long grupoId, Asignacion asignacion, long autorId) {
        existente(grupoId);
        if (fichas.fichaVigente(asignacion.especieId()).isEmpty()) {
            throw new NoEncontradoException("No existe la especie " + asignacion.especieId());
        }
        MembresiaGrupo nueva = new MembresiaGrupo(asignacion.especieId(), grupoId, asignacion.heredarFicha());
        grupos.cambiarMembresia(nueva);
        if (nueva.heredaFicha()) {
            grupos.fichaVigenteDelGrupo(grupoId).ifPresent(f -> copiarA(asignacion.especieId(), grupoId, f, autorId));
        }
        return grupo(grupoId);
    }

    /** Copia la ficha del grupo a cada especie que la hereda. Devuelve cuáles cambiaron. */
    private List<Long> propagar(long grupoId, FichaTaxonomica fichaDelGrupo, long autorId) {
        List<Long> actualizadas = new ArrayList<>();
        for (MembresiaGrupo m : grupos.miembros(grupoId)) {
            if (m.recibeCambiosDe(grupoId)) {
                copiarA(m.especieId(), grupoId, fichaDelGrupo, autorId);
                actualizadas.add(m.especieId());
            }
        }
        return actualizadas;
    }

    private void copiarA(long especieId, long grupoId, FichaTaxonomica fichaDelGrupo, long autorId) {
        FichaTaxonomica actual = fichas.fichaVigente(especieId)
                .orElseThrow(() -> new NoEncontradoException("No existe la especie " + especieId));
        grupos.guardarVersionHeredada(actual.adoptar(fichaDelGrupo), grupoId, fichaDelGrupo.version(), autorId);
        cachePublica.fichaCambio(especieId);
    }

    private GrupoTaxonomico existente(long grupoId) {
        return grupos.grupo(grupoId)
                .orElseThrow(() -> new NoEncontradoException("No existe el grupo " + grupoId));
    }

    private VistaGrupo vista(GrupoTaxonomico g) {
        return VistaGrupo.de(g, grupos.fichaVigenteDelGrupo(g.id()).orElse(null), grupos.miembros(g.id()));
    }
}
