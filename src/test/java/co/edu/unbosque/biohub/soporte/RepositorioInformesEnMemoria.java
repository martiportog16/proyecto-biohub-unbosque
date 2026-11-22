package co.edu.unbosque.biohub.soporte;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;

import co.edu.unbosque.biohub.dominio.analitica.InformeMensual;
import co.edu.unbosque.biohub.dominio.analitica.SolicitudInforme;
import co.edu.unbosque.biohub.persistencia.analitica.RepositorioInformes;

/**
 * FAKE del repositorio de informes (S20): funciona de verdad, pero en memoria.
 * Imita a la base en lo que importa: no deja guardar dos informes del mismo mes.
 */
public class RepositorioInformesEnMemoria implements RepositorioInformes {

    public final Map<YearMonth, InformeMensual> informes = new TreeMap<>();
    public final List<SolicitudInforme> solicitudes = new ArrayList<>();
    /** Para simular que la base no responde. */
    public boolean caida;

    private void revisar() {
        if (caida) {
            throw new IllegalStateException("La base de datos no responde");
        }
    }

    @Override
    public boolean existe(YearMonth periodo) {
        revisar();
        return informes.containsKey(periodo);
    }

    @Override
    public void guardar(InformeMensual informe) {
        revisar();
        if (informes.containsKey(informe.periodo())) {
            throw new DuplicateKeyException("Ya existe el informe de " + informe.periodo());
        }
        informes.put(informe.periodo(), informe);
    }

    @Override
    public Optional<InformeMensual> buscar(YearMonth periodo) {
        return Optional.ofNullable(informes.get(periodo));
    }

    @Override
    public List<ResumenInforme> recientes(int limite) {
        return informes.values().stream()
                .sorted(Comparator.comparing(InformeMensual::periodo).reversed())
                .limit(limite)
                .map(i -> new ResumenInforme(i.periodo(), i.generadoEn(), i.indices().size()))
                .toList();
    }

    @Override
    public long crearSolicitud(YearMonth periodo, long usuarioId, Instant cuando) {
        long id = solicitudes.size() + 1L;
        solicitudes.add(new SolicitudInforme(id, periodo, usuarioId, cuando, null, false));
        return id;
    }

    @Override
    public Optional<SolicitudInforme> solicitudPendiente(YearMonth periodo) {
        return solicitudes.stream().filter(s -> s.pendiente() && s.periodo().equals(periodo)).map(this::alDia).findFirst();
    }

    @Override
    public List<SolicitudInforme> solicitudesPendientes() {
        revisar();
        return solicitudes.stream().filter(SolicitudInforme::pendiente).map(this::alDia).toList();
    }

    @Override
    public List<SolicitudInforme> solicitudesRecientes(int limite) {
        return solicitudes.stream().sorted(Comparator.comparing(SolicitudInforme::id).reversed()).limit(limite)
                .map(this::alDia).toList();
    }

    @Override
    public void marcarAtendida(long solicitudId, Instant cuando) {
        solicitudes.replaceAll(s -> s.id() == solicitudId && s.pendiente()
                ? new SolicitudInforme(s.id(), s.periodo(), s.solicitadoPor(), s.solicitadoEn(), cuando, false) : s);
    }

    /** Como la consulta real: "conInforme" se calcula mirando si el informe existe en ese momento. */
    private SolicitudInforme alDia(SolicitudInforme s) {
        return new SolicitudInforme(s.id(), s.periodo(), s.solicitadoPor(), s.solicitadoEn(), s.atendidaEn(),
                informes.containsKey(s.periodo()));
    }
}
