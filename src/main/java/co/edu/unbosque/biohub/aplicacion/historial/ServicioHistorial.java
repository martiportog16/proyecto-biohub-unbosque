package co.edu.unbosque.biohub.aplicacion.historial;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.historial.NombresHistorial.Traductor;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaComparacion;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaHistorial;
import co.edu.unbosque.biohub.aplicacion.historial.VistasHistorial.VistaReclasificacion;
import co.edu.unbosque.biohub.dominio.historial.HistorialHallazgo;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;
import co.edu.unbosque.biohub.persistencia.historial.RepositorioHistorial;

/**
 * Caso de uso: consultar el historial y comparar estados (HU-07, CU-07).
 * Solo lectura: este servicio no tiene ninguna operación que escriba.
 */
@Service
public class ServicioHistorial {

    private final RepositorioHallazgos hallazgos;
    private final RepositorioHistorial historial;
    private final NombresHistorial nombres;
    private final Clock reloj;

    public ServicioHistorial(RepositorioHallazgos hallazgos, RepositorioHistorial historial,
                             NombresHistorial nombres, Clock reloj) {
        this.hallazgos = hallazgos;
        this.historial = historial;
        this.nombres = nombres;
        this.reloj = reloj;
    }

    public VistaHistorial historial(long hallazgoId) {
        HistorialHallazgo h = cargar(hallazgoId);
        Traductor t = nombres.traductor();
        return new VistaHistorial(hallazgoId, h.registradoEn(), t.especie(h.especieInicialId()),
                t.especie(h.especieActualId()), h.entradas().stream().map(t::vista).toList());
    }

    /**
     * Compara el estado al FINAL del día elegido (hora de Colombia) con el actual.
     * "Al final del día" para que cuenten los cambios hechos ese mismo día.
     */
    public VistaComparacion comparar(long hallazgoId, LocalDate fecha) {
        if (fecha == null) {
            throw new ReglaDeNegocioException("La comparación no es válida", List.of("fecha: es obligatoria"));
        }
        if (fecha.isAfter(LocalDate.now(reloj))) {
            throw new ReglaDeNegocioException("La comparación no es válida",
                    List.of("fecha: no puede ser una fecha futura"));
        }
        HistorialHallazgo h = cargar(hallazgoId);
        Instant finDelDia = fecha.plusDays(1).atStartOfDay(reloj.getZone()).toInstant().minusNanos(1);
        Optional<Long> entonces = h.especieEn(finDelDia);
        List<VistaReclasificacion> desdeEntonces = h.cambiosDespuesDe(finDelDia).stream()
                .map(nombres.traductor()::vista).toList();
        Traductor t = nombres.traductor();

        return new VistaComparacion(hallazgoId, fecha, entonces.isPresent(),
                entonces.map(t::especie).orElse(null), t.especie(h.especieActualId()),
                entonces.isPresent() && entonces.get() != h.especieActualId(), desdeEntonces);
    }

    private HistorialHallazgo cargar(long hallazgoId) {
        return hallazgos.buscar(hallazgoId)
                .map(hallazgo -> HistorialHallazgo.de(hallazgo, historial.deHallazgo(hallazgoId)))
                .orElseThrow(() -> new NoEncontradoException("No existe el hallazgo " + hallazgoId));
    }
}
