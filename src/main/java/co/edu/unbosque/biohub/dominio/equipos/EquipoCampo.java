package co.edu.unbosque.biohub.dominio.equipos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un equipo de campo (dron, cámara trampa, sensor...) y quién lo tiene a cargo (RF-03, HU-03).
 *
 * {@link #crear} es un método de creación que hace cumplir la regla principal: NINGÚN equipo
 * existe sin responsable (HU-03, criterio 4). No es el patrón Factory Method (ver Entregable 2).
 *
 * @param id null mientras no se ha guardado
 */
public record EquipoCampo(Long id, String codigo, TipoEquipo tipo, String descripcion, Long responsableId) {

    public static final int LARGO_MINIMO_CODIGO = 3;
    public static final int LARGO_MAXIMO_CODIGO = 40;
    public static final int LARGO_MINIMO_MOTIVO = 5;

    /** Valida todo a la vez y devuelve todos los errores juntos, para que el usuario corrija una sola vez. */
    public static EquipoCampo crear(String codigo, String tipo, String descripcion, Long responsableId) {
        List<String> errores = new ArrayList<>();
        String codigoNormal = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (codigoNormal.isEmpty()) {
            errores.add("codigo: es obligatorio");
        } else if (codigoNormal.length() < LARGO_MINIMO_CODIGO || codigoNormal.length() > LARGO_MAXIMO_CODIGO
                || !codigoNormal.matches("[A-Z0-9-]+")) {
            errores.add("codigo: use de " + LARGO_MINIMO_CODIGO + " a " + LARGO_MAXIMO_CODIGO
                    + " letras, números o guiones (por ejemplo DRON-007)");
        }
        TipoEquipo tipoEquipo = null;
        if (tipo == null || tipo.isBlank()) {
            errores.add("tipo: es obligatorio");
        } else {
            try {
                tipoEquipo = TipoEquipo.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errores.add("tipo: '" + tipo + "' no existe. Use uno de " + List.of(TipoEquipo.values()));
            }
        }
        if (responsableId == null) {
            errores.add("responsableId: un equipo no se puede registrar sin responsable");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeEquipoException(errores);
        }
        String desc = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        return new EquipoCampo(null, codigoNormal, tipoEquipo, desc, responsableId);
    }

    public EquipoCampo conId(long nuevoId) {
        return new EquipoCampo(nuevoId, codigo, tipo, descripcion, responsableId);
    }

    /** Pasa el equipo a otra persona. El motivo queda en el historial de asignaciones. */
    public EquipoCampo reasignarA(Long nuevoResponsable, String motivo) {
        List<String> errores = new ArrayList<>();
        if (nuevoResponsable == null) {
            errores.add("responsableId: un equipo no puede quedar sin responsable");
        } else if (nuevoResponsable.equals(responsableId)) {
            errores.add("responsableId: el equipo ya está a cargo de esa persona");
        }
        if (motivo == null || motivo.trim().length() < LARGO_MINIMO_MOTIVO) {
            errores.add("motivo: explique en al menos " + LARGO_MINIMO_MOTIVO + " caracteres por qué cambia el responsable");
        }
        if (!errores.isEmpty()) {
            throw new ReglaDeEquipoException(errores);
        }
        return new EquipoCampo(id, codigo, tipo, descripcion, nuevoResponsable);
    }
}
