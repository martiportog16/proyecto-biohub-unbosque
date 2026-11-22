package co.edu.unbosque.biohub.dominio.fichas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.fichas.validacion.CatalogoValidadores;

/**
 * La ficha de una especie o de un grupo taxonómico: qué atributos se registran en
 * los hallazgos (RNF-01, ADR-07). El primer número es el de su dueño: la especie,
 * o el grupo cuando es la ficha de un grupo (ver FichaDeGrupo).
 *
 * Es INMUTABLE y VERSIONADA: agregar o retirar un atributo crea una versión nueva.
 * Así cada hallazgo sigue apuntando a la versión con la que se registró y ningún
 * registro viejo se ve afectado (0 registros afectados).
 */
public record FichaTaxonomica(long especieId, int version, List<DefinicionAtributo> atributos) {

    public FichaTaxonomica {
        atributos = List.copyOf(atributos);
    }

    public static FichaTaxonomica inicial(long especieId, List<DefinicionAtributo> atributos) {
        FichaTaxonomica vacia = new FichaTaxonomica(especieId, 0, List.of());
        FichaTaxonomica ficha = vacia;
        for (DefinicionAtributo a : atributos) {
            ficha = ficha.conAtributo(a);
        }
        return new FichaTaxonomica(especieId, 1, ficha.atributos());
    }

    public List<DefinicionAtributo> activos() {
        return atributos.stream().filter(DefinicionAtributo::activo).toList();
    }

    public Optional<DefinicionAtributo> buscar(String nombre) {
        return atributos.stream().filter(a -> a.nombre().equals(nombre)).findFirst();
    }

    /** Nueva versión con un atributo más. El nombre no puede repetirse, ni con uno retirado. */
    public FichaTaxonomica conAtributo(DefinicionAtributo nuevo) {
        List<String> errores = new ArrayList<>(nuevo.erroresDeDefinicion());
        if (nuevo.nombre() != null && buscar(nuevo.nombre()).isPresent()) {
            errores.add("nombre: ya existe un atributo llamado '" + nuevo.nombre() + "' (activo o retirado)");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeFichaException(errores);
        }
        List<DefinicionAtributo> lista = new ArrayList<>(atributos);
        lista.add(new DefinicionAtributo(nuevo.nombre(), nuevo.tipo(), nuevo.obligatorio(), true,
                nuevo.opciones(), nuevo.unidad()));
        return new FichaTaxonomica(especieId, version + 1, lista);
    }

    /** Nueva versión con el atributo marcado como inactivo. Sus valores viejos se conservan. */
    public FichaTaxonomica sinAtributo(String nombre) {
        DefinicionAtributo actual = buscar(nombre)
                .orElseThrow(() -> new ReglaDeFichaException(List.of("nombre: no existe el atributo '" + nombre + "'")));
        if (!actual.activo()) {
            throw new ReglaDeFichaException(List.of("nombre: el atributo '" + nombre + "' ya estaba retirado"));
        }
        List<DefinicionAtributo> lista = atributos.stream()
                .map(a -> a.nombre().equals(nombre) ? a.retirado() : a)
                .toList();
        return new FichaTaxonomica(especieId, version + 1, lista);
    }

    /**
     * La especie adopta la ficha de su grupo taxonómico (RF-02: ficha "por especie o
     * categoría", diagrama de clases: FichaTaxonomica define para Especie o GrupoTaxonomico).
     *
     * Devuelve la versión SIGUIENTE de la ficha de esta especie, con:
     *  - todos los atributos de la ficha del grupo, tal como están en el grupo
     *    (activos o retirados); si un nombre coincide, manda la definición del grupo;
     *  - los atributos propios de la especie que el grupo no tiene, pero RETIRADOS:
     *    ya no se piden en hallazgos nuevos, y los valores viejos se conservan (RNF-01).
     *
     * Es una copia: así cada hallazgo sigue apuntando a una versión fija de la ficha de
     * su especie (PR-01) y nada del registro, la consulta pública ni la API externa cambia.
     */
    public FichaTaxonomica adoptar(FichaTaxonomica fichaDelGrupo) {
        List<DefinicionAtributo> lista = new ArrayList<>(fichaDelGrupo.atributos());
        for (DefinicionAtributo propio : atributos) {
            if (fichaDelGrupo.buscar(propio.nombre()).isEmpty()) {
                lista.add(propio.activo() ? propio.retirado() : propio);
            }
        }
        return new FichaTaxonomica(especieId, version + 1, lista);
    }

    /**
     * Valida los valores de un hallazgo contra esta versión de la ficha.
     * Reúne TODOS los errores antes de responder (HU-01, criterio 2).
     * Para cada atributo usa la estrategia de su tipo (patrón Strategy).
     */
    public List<String> validar(Map<String, Object> valores, CatalogoValidadores catalogo) {
        List<String> errores = new ArrayList<>();

        for (String nombre : valores.keySet()) {
            Optional<DefinicionAtributo> definicion = buscar(nombre);
            if (definicion.isEmpty()) {
                errores.add(nombre + ": no es un atributo de esta ficha");
            } else if (!definicion.get().activo()) {
                errores.add(nombre + ": el atributo fue retirado y ya no se registra");
            }
        }

        for (DefinicionAtributo definicion : activos()) {
            Object valor = valores.get(definicion.nombre());
            if (valor == null) {
                if (definicion.obligatorio()) {
                    errores.add(definicion.nombre() + ": es obligatorio");
                }
                continue;
            }
            catalogo.para(definicion.tipo())
                    .validar(definicion, valor)
                    .ifPresent(motivo -> errores.add(definicion.nombre() + ": " + motivo));
        }
        return errores;
    }
}
