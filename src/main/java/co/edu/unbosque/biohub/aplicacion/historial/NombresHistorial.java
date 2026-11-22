package co.edu.unbosque.biohub.aplicacion.historial;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.AutorResumen;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.EspecieResumen;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaReclasificacion;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.historial.Reclasificacion;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * Convierte los ids del historial en nombres legibles para el auditor
 * (HU-07, criterio 2: "autor" y "valor anterior/nuevo" con nombre, no solo un número).
 */
@Component
public class NombresHistorial {

    private final RepositorioFichas fichas;
    private final RepositorioUsuarios usuarios;

    public NombresHistorial(RepositorioFichas fichas, RepositorioUsuarios usuarios) {
        this.fichas = fichas;
        this.usuarios = usuarios;
    }

    /** Carga los nombres de las especies una sola vez por consulta. */
    public Traductor traductor() {
        Map<Long, Especie> especies = fichas.especies().stream()
                .collect(Collectors.toMap(Especie::id, Function.identity()));
        return new Traductor(especies);
    }

    public final class Traductor {

        private final Map<Long, Especie> especies;

        private Traductor(Map<Long, Especie> especies) {
            this.especies = especies;
        }

        public EspecieResumen especie(long id) {
            Especie e = especies.get(id);
            return new EspecieResumen(id, e == null ? "(especie " + id + ")" : e.nombreCientifico());
        }

        public AutorResumen autor(long id) {
            String nombre = usuarios.buscarPorId(id).map(u -> u.nombre()).orElse("(usuario " + id + ")");
            return new AutorResumen(id, nombre);
        }

        public VistaReclasificacion vista(Reclasificacion r) {
            return new VistaReclasificacion(r.id(), r.hallazgoId(), especie(r.especieAnteriorId()),
                    especie(r.especieNuevaId()), r.motivo(), autor(r.autorId()), r.realizadaEn());
        }
    }
}
