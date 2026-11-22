package co.edu.unbosque.biohub.presentacion.analitica;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.aplicacion.analitica.ServicioInformes;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.EstadoSolicitud;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.NuevaSolicitud;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaInforme;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaResumenInforme;
import co.edu.unbosque.biohub.aplicacion.analitica.VistasInformes.VistaSolicitud;
import co.edu.unbosque.biohub.presentacion.comun.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Informes mensuales de presión para el analista (HU-11).
 * El mes siempre va en formato AAAA-MM, por ejemplo 2026-10.
 */
@RestController
@RequestMapping("/api/cientifico/informes")
@PreAuthorize("hasRole('ANALISTA')")
@Tag(name = "Informes de presión")
public class InformesController {

    private final ServicioInformes informes;

    public InformesController(ServicioInformes informes) {
        this.informes = informes;
    }

    @GetMapping
    @Operation(summary = "Informes generados", description = "Del más reciente al más antiguo, sin los índices.")
    public List<VistaResumenInforme> recientes() {
        return informes.recientes();
    }

    @GetMapping("/{periodo}")
    @Operation(summary = "Informe de un mes",
               description = "Índice de presión de cada departamento, de mayor a menor. 0 = sin presión, 1 = no se vio ninguna especie.")
    public VistaInforme informe(@PathVariable YearMonth periodo) {
        return informes.informe(periodo);
    }

    @PostMapping("/solicitudes")
    @Operation(summary = "Pedir el informe de un mes",
               description = "Responde 202: el proceso batch lo genera aparte, en menos de 1 minuto. Si el informe ya existe responde 200.")
    public ResponseEntity<VistaSolicitud> solicitar(@RequestBody NuevaSolicitud datos,
                                                    @AuthenticationPrincipal Jwt token) {
        VistaSolicitud solicitud = informes.solicitar(datos.periodo(), UsuarioActual.de(token).id());
        HttpStatus estado = solicitud.estado() == EstadoSolicitud.INFORME_YA_EXISTE ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado).body(solicitud);
    }

    @GetMapping("/solicitudes")
    @Operation(summary = "Solicitudes recientes", description = "Para saber si una solicitud sigue pendiente o ya fue atendida.")
    public List<VistaSolicitud> solicitudes() {
        return informes.solicitudesRecientes();
    }
}
