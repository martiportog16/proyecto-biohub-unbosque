-- =====================================================================
-- V4 · Historial de reclasificaciones (paquete 05, Martin) · HU-06, RNF-05, P-17
--
-- Cada reclasificación es una fila NUEVA. El historial nunca se edita ni se borra.
-- Tres capas de protección (si una falla, quedan las otras):
--   1. Dominio: HistorialHallazgo solo sabe "agregar".
--   2. Permisos: app_cientifico solo puede leer e insertar aquí (este archivo).
--   3. Trigger: rechaza UPDATE, DELETE y TRUNCATE venga de quien venga (este archivo).
-- =====================================================================

CREATE TABLE reclasificacion (
    id                  BIGSERIAL    PRIMARY KEY,
    hallazgo_id         BIGINT       NOT NULL REFERENCES hallazgo(id),
    especie_anterior_id BIGINT       NOT NULL REFERENCES especie(id),   -- valor anterior
    especie_nueva_id    BIGINT       NOT NULL REFERENCES especie(id),   -- valor nuevo
    motivo              TEXT         NOT NULL CHECK (length(btrim(motivo)) >= 10),
    autor_id            BIGINT       NOT NULL REFERENCES usuario(id),
    realizada_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (especie_anterior_id <> especie_nueva_id)
);

-- HU-07: el historial de un hallazgo se lee siempre en orden cronológico.
CREATE INDEX idx_reclasificacion_hallazgo ON reclasificacion (hallazgo_id, realizada_en, id);

-- Capa 2 · Permisos: la app puede leer e insertar, nada más.
REVOKE UPDATE, DELETE, TRUNCATE ON reclasificacion FROM app_cientifico;

-- Del hallazgo, la app solo puede cambiar la especie (para reclasificar).
-- Los valores, la fecha y el departamento quedan como se registraron.
REVOKE UPDATE ON hallazgo FROM app_cientifico;
GRANT UPDATE (especie_id) ON hallazgo TO app_cientifico;

-- Capa 3 · Trigger: aunque alguien tenga permisos (por ejemplo el dueño de la
-- base conectado por SQL directo), cualquier UPDATE, DELETE o TRUNCATE falla.
CREATE FUNCTION rechazar_cambios_historial() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial de reclasificaciones es inmutable: no se permite %', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER historial_sin_cambios
    BEFORE UPDATE OR DELETE ON reclasificacion
    FOR EACH ROW EXECUTE FUNCTION rechazar_cambios_historial();

CREATE TRIGGER historial_sin_truncate
    BEFORE TRUNCATE ON reclasificacion
    FOR EACH STATEMENT EXECUTE FUNCTION rechazar_cambios_historial();
