package co.edu.unbosque.biohub.dominio.historial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;

/**
 * El historial de reclasificaciones de un hallazgo (RNF-05, P-17).
 *
 * Primera capa de protección: esta clase solo sabe AGREGAR. No tiene métodos
 * para editar ni para borrar, y la lista que entrega no se puede modificar.
 * Agregar no cambia este objeto: devuelve un historial nuevo (inmutable).
 */
public final class HistorialHallazgo {

    private final long hallazgoId;
    private final long especieInicialId;
    private final Instant registradoEn;
    private final List<Reclasificacion> entradas;

    private HistorialHallazgo(long hallazgoId, long especieInicialId, Instant registradoEn,
                              List<Reclasificacion> entradas) {
        this.hallazgoId = hallazgoId;
        this.especieInicialId = especieInicialId;
        this.registradoEn = registradoEn;
        this.entradas = List.copyOf(entradas);
    }

    /**
     * Arma el historial de un hallazgo a partir de sus reclasificaciones guardadas.
     * La especie con la que se registró es la "anterior" de la primera reclasificación;
     * si nunca se reclasificó, es la especie actual del hallazgo.
     */
    public static HistorialHallazgo de(Hallazgo hallazgo, List<Reclasificacion> guardadas) {
        List<Reclasificacion> ordenadas = new ArrayList<>(guardadas);
        ordenadas.sort(Comparator.comparing(Reclasificacion::realizadaEn)
                .thenComparing(r -> r.id() == null ? Long.MAX_VALUE : r.id()));
        long inicial = ordenadas.isEmpty() ? hallazgo.especieId() : ordenadas.get(0).especieAnteriorId();
        return new HistorialHallazgo(hallazgo.id(), inicial, hallazgo.registradoEn(), ordenadas);
    }

    /**
     * Devuelve un historial nuevo con la reclasificación al final.
     * Rechaza entradas que romperían la cadena: cada cambio debe partir de la
     * especie actual y no puede tener una fecha anterior al último cambio.
     */
    public HistorialHallazgo agregar(Reclasificacion nueva) {
        if (nueva.hallazgoId() != hallazgoId) {
            throw new IllegalArgumentException("La reclasificación es de otro hallazgo");
        }
        if (nueva.especieAnteriorId() != especieActualId()) {
            throw new IllegalArgumentException("La reclasificación debe partir de la especie actual del hallazgo");
        }
        if (!entradas.isEmpty() && nueva.realizadaEn().isBefore(entradas.get(entradas.size() - 1).realizadaEn())) {
            throw new IllegalArgumentException("No se puede agregar un cambio con fecha anterior al último");
        }
        List<Reclasificacion> todas = new ArrayList<>(entradas);
        todas.add(nueva);
        return new HistorialHallazgo(hallazgoId, especieInicialId, registradoEn, todas);
    }

    /** Las reclasificaciones en orden cronológico (HU-07, criterio 1). No se puede modificar. */
    public List<Reclasificacion> entradas() {
        return entradas;
    }

    /**
     * El "estado" del hallazgo en un momento dado (P-29): la especie que tenía asignada.
     * Se reconstruye recorriendo el historial hasta ese momento (incluido).
     * Vacío si en ese momento el hallazgo todavía no se había registrado.
     */
    public Optional<Long> especieEn(Instant momento) {
        if (momento.isBefore(registradoEn)) {
            return Optional.empty();
        }
        long especie = especieInicialId;
        for (Reclasificacion r : entradas) {
            if (r.realizadaEn().isAfter(momento)) {
                break;
            }
            especie = r.especieNuevaId();
        }
        return Optional.of(especie);
    }

    /** Los cambios hechos DESPUÉS del momento dado: lo que cambió desde entonces hasta hoy. */
    public List<Reclasificacion> cambiosDespuesDe(Instant momento) {
        return entradas.stream().filter(r -> r.realizadaEn().isAfter(momento)).toList();
    }

    public long especieActualId() {
        return entradas.isEmpty() ? especieInicialId : entradas.get(entradas.size() - 1).especieNuevaId();
    }

    public long especieInicialId() {
        return especieInicialId;
    }

    public long hallazgoId() {
        return hallazgoId;
    }

    public Instant registradoEn() {
        return registradoEn;
    }
}
