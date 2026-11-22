package co.edu.unbosque.biohub.aplicacion.fichas;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.consultapublica.InvalidadorCachePublica;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevaEspecie;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.NuevoAtributo;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.VistaEspecie;
import co.edu.unbosque.biohub.aplicacion.fichas.VistasFichas.VistaFicha;
import co.edu.unbosque.biohub.dominio.fichas.DefinicionAtributo;
import co.edu.unbosque.biohub.dominio.fichas.FichaTaxonomica;
import co.edu.unbosque.biohub.dominio.fichas.MembresiaGrupo;
import co.edu.unbosque.biohub.dominio.fichas.ReglaDeFichaException;
import co.edu.unbosque.biohub.dominio.fichas.TipoDato;
import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioGrupos;

/**
 * Casos de uso de las fichas flexibles (HU-02, CU-02, secuencia 7).
 * Agregar o retirar un atributo es un CAMBIO DE DATOS: crea una versión nueva de la
 * ficha sin tocar código ni registros existentes (RNF-01).
 */
@Service
public class ServicioFichas {

    private final RepositorioFichas repositorio;
    private final CatalogoValidadores catalogo;
    private final InvalidadorCachePublica cachePublica;
    private final RepositorioGrupos grupos;

    public ServicioFichas(RepositorioFichas repositorio, CatalogoValidadores catalogo,
                          InvalidadorCachePublica cachePublica, RepositorioGrupos grupos) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.cachePublica = cachePublica;
        this.grupos = grupos;
    }

    public List<VistaEspecie> especies() {
        return repositorio.especies().stream().map(VistaEspecie::de).toList();
    }

    public VistaFicha fichaVigente(long especieId) {
        return VistaFicha.de(vigente(especieId));
    }

    @Transactional
    public VistaFicha crearEspecie(NuevaEspecie datos, long autorId) {
        // Todos los errores juntos, para corregir de una vez. El nombre común es obligatorio
        // porque es lo primero que lee un visitante en el portal (RNF-10).
        List<String> errores = new java.util.ArrayList<>();
        validarNombre("nombreCientifico", datos.nombreCientifico(), errores);
        validarNombre("nombreComun", datos.nombreComun(), errores);
        if (errores.isEmpty() && repositorio.existeNombreCientifico(datos.nombreCientifico().trim())) {
            errores.add("nombreCientifico: ya existe '" + datos.nombreCientifico().trim() + "'");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("La especie no es válida", errores);
        }
        long id = repositorio.crearEspecie(datos.nombreCientifico().trim(), datos.nombreComun().trim(), datos.enPeligro());
        List<NuevoAtributo> atributos = datos.atributos() == null ? List.of() : datos.atributos();
        FichaTaxonomica ficha = traducir(() ->
                FichaTaxonomica.inicial(id, atributos.stream().map(ServicioFichas::aDefinicion).toList()));
        repositorio.guardarVersion(ficha, autorId);
        cachePublica.fichaCambio(id);
        return VistaFicha.de(ficha);
    }

    private static final int LARGO_MAXIMO_NOMBRE = 150;

    private static void validarNombre(String campo, String valor, List<String> errores) {
        if (valor == null || valor.isBlank()) {
            errores.add(campo + ": es obligatorio");
        } else if (valor.trim().length() > LARGO_MAXIMO_NOMBRE) {
            errores.add(campo + ": no puede tener más de " + LARGO_MAXIMO_NOMBRE + " caracteres");
        }
    }

    @Transactional
    public VistaFicha agregarAtributo(long especieId, NuevoAtributo atributo, long autorId) {
        FichaTaxonomica nueva = traducir(() -> vigente(especieId).conAtributo(aDefinicion(atributo)));
        repositorio.guardarVersion(nueva, autorId);
        independizarDelGrupo(especieId);
        cachePublica.fichaCambio(especieId);
        return VistaFicha.de(nueva);
    }

    @Transactional
    public VistaFicha retirarAtributo(long especieId, String nombre, long autorId) {
        FichaTaxonomica nueva = traducir(() -> vigente(especieId).sinAtributo(nombre));
        repositorio.guardarVersion(nueva, autorId);
        independizarDelGrupo(especieId);
        cachePublica.fichaCambio(especieId);
        return VistaFicha.de(nueva);
    }

    /**
     * Valida los valores de un hallazgo contra la ficha vigente y devuelve su versión.
     * Lo usa el registro de hallazgos (HU-01).
     */
    public int validarValores(long especieId, Map<String, Object> valores) {
        FichaTaxonomica ficha = vigente(especieId);
        List<String> errores = ficha.validar(valores, catalogo);
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("Los valores no cumplen la ficha de la especie", errores);
        }
        return ficha.version();
    }

    /**
     * Si el administrador cambia directamente la ficha de una especie que heredaba la de
     * su grupo, la especie pasa a tener ficha propia: el próximo cambio del grupo ya no
     * le borra lo que se hizo a mano. Sigue perteneciendo al grupo.
     */
    private void independizarDelGrupo(long especieId) {
        MembresiaGrupo m = grupos.membresia(especieId);
        if (m.heredaFicha()) {
            grupos.cambiarMembresia(new MembresiaGrupo(especieId, m.grupoId(), false));
        }
    }

    private FichaTaxonomica vigente(long especieId) {
        return repositorio.fichaVigente(especieId)
                .orElseThrow(() -> new NoEncontradoException("No existe la especie " + especieId));
    }

    /** También lo usa ServicioGruposTaxonomicos: un atributo se define igual para una especie o un grupo. */
    static DefinicionAtributo aDefinicion(NuevoAtributo a) {
        TipoDato tipo = null;
        try {
            tipo = a.tipo() == null ? null : TipoDato.valueOf(a.tipo().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioException("La ficha no es válida",
                    List.of("tipo: '" + a.tipo() + "' no existe. Use uno de " + List.of(TipoDato.values())));
        }
        return new DefinicionAtributo(a.nombre(), tipo, a.obligatorio(), true, a.opciones(), a.unidad());
    }

    /** Convierte la regla violada del dominio en el error que entiende la presentación. */
    static FichaTaxonomica traducir(java.util.function.Supplier<FichaTaxonomica> cambio) {
        try {
            return cambio.get();
        } catch (ReglaDeFichaException e) {
            throw new ReglaDeNegocioException("La ficha no es válida", e.getErrores());
        }
    }
}
