package co.edu.unbosque.biohub.aplicacion.autorizacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaAcceso;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaCoordenada;
import co.edu.unbosque.biohub.aplicacion.autorizacion.VistasCoordenadas.VistaMapa;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.dominio.coordenadas.AccesoCoordenadas;
import co.edu.unbosque.biohub.dominio.coordenadas.ReglaAccesoCoordenadas;
import co.edu.unbosque.biohub.dominio.coordenadas.ReglaAccesoCoordenadas.Motivo;
import co.edu.unbosque.biohub.dominio.coordenadas.UbicacionExacta;
import co.edu.unbosque.biohub.dominio.fichas.DatosDeUbicacion;
import co.edu.unbosque.biohub.dominio.fichas.Especie;
import co.edu.unbosque.biohub.dominio.hallazgos.Hallazgo;
import co.edu.unbosque.biohub.dominio.usuarios.Usuario;
import co.edu.unbosque.biohub.persistencia.coordenadas.LecturaCoordenadas;
import co.edu.unbosque.biohub.persistencia.coordenadas.RegistroAccesos;
import co.edu.unbosque.biohub.persistencia.fichas.RepositorioFichas;
import co.edu.unbosque.biohub.persistencia.hallazgos.RepositorioHallazgos;
import co.edu.unbosque.biohub.persistencia.usuarios.RepositorioUsuarios;

/**
 * EL componente centralizado de autorización de coordenadas (RNF-06, HU-08 criterio 3).
 *
 * El 100 % de las consultas de coordenadas exactas pasa por aquí: es la ÚNICA clase
 * que recibe LecturaCoordenadas (C-3). Para cada consulta, siempre en este orden:
 *
 *   1. Busca al usuario en la BASE (no confía en el rol del token: si le quitaron
 *      el rol o lo desactivaron hace 5 minutos, el token viejo ya no sirve aquí).
 *   2. Aplica la regla P-24 (ReglaAccesoCoordenadas) con la especie ACTUAL del hallazgo.
 *   3. REGISTRA la decisión, permitida o negada (P-18).
 *   4. Solo si se permitió, lee las coordenadas.
 *
 * Detalles que importan:
 * - Registrar va ANTES de leer. Si el registro falla, no se entregan coordenadas
 *   ("falla cerrado"): nunca hay un acceso sin su fila de evidencia.
 * - Este servicio NO es @Transactional a propósito: cuando se niega el acceso se
 *   lanza una excepción, y dentro de una transacción esa excepción desharía también
 *   la fila del registro. Así el intento negado queda guardado.
 */
@Service
public class AutorizacionCoordenadas {

    /** Tope de puntos del mapa de una especie: los hallazgos más recientes. */
    static final int MAXIMO_PUNTOS_MAPA = 500;
    static final int MAXIMO_ACCESOS = 200;

    private final LecturaCoordenadas lectura;
    private final RegistroAccesos accesos;
    private final RepositorioHallazgos hallazgos;
    private final RepositorioFichas fichas;
    private final RepositorioUsuarios usuarios;
    private final Clock reloj;

    public AutorizacionCoordenadas(LecturaCoordenadas lectura, RegistroAccesos accesos,
                                   RepositorioHallazgos hallazgos, RepositorioFichas fichas,
                                   RepositorioUsuarios usuarios, Clock reloj) {
        this.lectura = lectura;
        this.accesos = accesos;
        this.hallazgos = hallazgos;
        this.fichas = fichas;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** La coordenada exacta de un hallazgo, si el usuario puede verla. */
    public VistaCoordenada coordenadaDe(long hallazgoId, long usuarioId) {
        Hallazgo hallazgo = hallazgos.buscar(hallazgoId)
                .orElseThrow(() -> new NoEncontradoException("No existe el hallazgo " + hallazgoId));
        Especie especie = especie(hallazgo.especieId());

        autorizar(usuarioId, especie, hallazgoId);

        // Ya autorizado: también los valores de la ficha que revelan la ubicación (ruta GPS, archivos)
        Map<String, Object> datosDeUbicacion = fichas.fichaEnVersion(hallazgo.fichaEspecieId(), hallazgo.versionFicha())
                .map(f -> DatosDeUbicacion.separar(f, hallazgo.valores()).ubicacion())
                .orElse(Map.of());
        return lectura.deHallazgo(hallazgoId).map(u -> vista(u, datosDeUbicacion))
                .orElseThrow(() -> new NoEncontradoException("El hallazgo " + hallazgoId + " no tiene coordenada"));
    }

    /** Los puntos exactos de una especie para el mapa, si el usuario puede verlos. */
    public VistaMapa mapaDeEspecie(long especieId, long usuarioId) {
        Especie especie = especie(especieId);

        autorizar(usuarioId, especie, null);

        List<VistaCoordenada> puntos = lectura.deEspecie(especieId, MAXIMO_PUNTOS_MAPA).stream()
                .map(u -> vista(u, Map.of())).toList();
        return new VistaMapa(especie.id(), especie.nombreCientifico(), especie.enPeligro(), puntos.size(), puntos);
    }

    /** El registro de accesos para el auditor: quién pidió qué y qué se decidió. */
    public List<VistaAcceso> accesosRecientes(boolean soloNegados, int limite) {
        int tope = Math.max(1, Math.min(limite, MAXIMO_ACCESOS));
        return accesos.recientes(soloNegados, tope).stream()
                .map(a -> new VistaAcceso(a.id(), a.usuarioId(), a.rol(), a.especieId(), a.hallazgoId(),
                        a.especieEnPeligro(), a.permitido(), a.motivo(), a.ocurridoEn()))
                .toList();
    }

    /** Pasos 1 a 3. Termina normalmente solo si el acceso se permitió y quedó registrado. */
    private void autorizar(long usuarioId, Especie especie, Long hallazgoId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new AccesoCoordenadasDenegadoException("Usuario desconocido"));

        Motivo motivo = ReglaAccesoCoordenadas.evaluar(usuario.rol(), usuario.activo(), especie.enPeligro());

        accesos.registrar(new AccesoCoordenadas(null, usuarioId, usuario.rol().name(), especie.id(), hallazgoId,
                especie.enPeligro(), motivo.permite(), motivo.name(), Instant.now(reloj)));

        if (!motivo.permite()) {
            throw new AccesoCoordenadasDenegadoException(motivo.descripcion()
                    + ". Puede ver el departamento en los datos del hallazgo.");
        }
    }

    private Especie especie(long especieId) {
        return fichas.especie(especieId)
                .orElseThrow(() -> new NoEncontradoException("No existe la especie " + especieId));
    }

    private static VistaCoordenada vista(UbicacionExacta u, Map<String, Object> datosDeUbicacion) {
        return new VistaCoordenada(u.hallazgoId(), u.departamento(), u.fechaObservacion(),
                u.coordenada().latitud(), u.coordenada().longitud(), u.coordenada().precisionMetros(), datosDeUbicacion);
    }
}
