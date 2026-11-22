package co.edu.unbosque.biohub.aplicacion.analitica;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.EstadoSolicitud;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaIndice;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaInforme;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaResumenInforme;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaSolicitud;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.dominio.analitica.CalculadoraIndicePresion;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;
import co.edu.unbosque.biohub.persistencia.analitica.RepositorioInformes;

/**
 * Lo que el analista hace desde la API (HU-11, criterio 4): consultar los informes
 * y pedir uno. Corre en el grupo científico.
 *
 * Aquí NUNCA se calcula un informe: pedirlo solo guarda una fila "solicitud" y el
 * proceso batch la atiende aparte. Así el cálculo pesado no usa los recursos del
 * registro diario (HU-11, criterio 2).
 */
@Service
public class ServicioInformes {

    private static final int MAXIMO = 60;

    private final RepositorioInformes informes;
    private final Clock reloj;

    public ServicioInformes(RepositorioInformes informes, Clock reloj) {
        this.informes = informes;
        this.reloj = reloj;
    }

    public List<VistaResumenInforme> recientes() {
        return informes.recientes(MAXIMO).stream()
                .map(r -> new VistaResumenInforme(r.periodo(), r.generadoEn(), r.totalDepartamentos()))
                .toList();
    }

    public VistaInforme informe(YearMonth periodo) {
        return informes.buscar(periodo)
                .map(i -> new VistaInforme(i.periodo(), i.generadoEn(), i.indices().size(),
                        i.indices().stream().map(x -> new VistaIndice(x.departamento(), x.valor(),
                                x.especiesEnElMes(), x.base(), x.sinDatos())).toList()))
                .orElseThrow(() -> new NoEncontradoException("Todavía no hay informe de " + periodo));
    }

    /**
     * Pide el informe de un mes. No duplica trabajo:
     *  - si el informe ya existe, lo dice y no crea nada;
     *  - si ya hay una solicitud pendiente de ese mes, devuelve esa misma.
     */
    public VistaSolicitud solicitar(YearMonth periodo, long usuarioId) {
        if (periodo == null) {
            throw new ReglaDeNegocioException("La solicitud no es válida",
                    List.of("periodo: es obligatorio, en formato AAAA-MM"));
        }
        if (!CalculadoraIndicePresion.sePuedeGenerar(periodo, LocalDate.now(reloj))) {
            throw new ReglaDeNegocioException("La solicitud no es válida",
                    List.of("periodo: solo se generan informes de meses que ya terminaron"));
        }
        if (informes.existe(periodo)) {
            return new VistaSolicitud(null, periodo, EstadoSolicitud.INFORME_YA_EXISTE, null, null);
        }
        Optional<SolicitudInforme> pendiente = informes.solicitudPendiente(periodo);
        if (pendiente.isPresent()) {
            return vista(pendiente.get());
        }
        Instant ahora = Instant.now(reloj);
        long id = informes.crearSolicitud(periodo, usuarioId, ahora);
        return new VistaSolicitud(id, periodo, EstadoSolicitud.PENDIENTE, ahora, null);
    }

    public List<VistaSolicitud> solicitudesRecientes() {
        return informes.solicitudesRecientes(MAXIMO).stream().map(ServicioInformes::vista).toList();
    }

    private static VistaSolicitud vista(SolicitudInforme s) {
        EstadoSolicitud estado = s.pendiente() ? EstadoSolicitud.PENDIENTE
                : s.conInforme() ? EstadoSolicitud.ATENDIDA : EstadoSolicitud.SIN_DATOS;
        return new VistaSolicitud(s.id(), s.periodo(), estado, s.solicitadoEn(), s.atendidaEn());
    }
}
