-- =====================================================================
-- V11 · Gestión de usuarios y bitácora de cambios (paquete 28, Paula) · P-28, S-01
--
-- La Entrega 2 dejó pendiente "autenticación y gestión de usuarios" (P-28).
-- Desde aquí, los usuarios se crean, cambian de rol y se desactivan desde la consola:
--   - el administrador de la plataforma crea usuarios, cambia roles y los desactiva;
--   - dar o quitar el rol de alto rango solo lo hace un director científico (S-01).
-- Cada cambio queda en una bitácora de solo inserción, como el historial (RNF-05).
-- =====================================================================

CREATE TABLE cambio_usuario (
    id            BIGSERIAL    PRIMARY KEY,
    usuario_id    BIGINT       NOT NULL REFERENCES usuario (id),
    autor_id      BIGINT       NOT NULL REFERENCES usuario (id),
    realizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tipo          VARCHAR(20)  NOT NULL CHECK (tipo IN ('ALTA', 'CAMBIO_ROL', 'DESACTIVACION', 'REACTIVACION', 'CAMBIO_CLAVE')),
    rol_anterior  VARCHAR(40),
    rol_nuevo     VARCHAR(40),
    motivo        VARCHAR(500) NOT NULL CHECK (length(trim(motivo)) >= 5)
);

CREATE INDEX idx_cambio_usuario_usuario ON cambio_usuario (usuario_id, realizado_en);

-- La bitácora: la aplicación solo lee e inserta. El grupo público y el batch no la ven.
REVOKE UPDATE, DELETE, TRUNCATE ON cambio_usuario FROM app_cientifico;
REVOKE ALL ON cambio_usuario FROM app_publico, app_batch;

-- Y aunque alguien entre a la base con más permisos, no puede borrar ni cambiar el rastro.
CREATE FUNCTION rechazar_cambios_bitacora_usuarios() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora de cambios de usuarios es inmutable: no se permite %', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER bitacora_usuarios_sin_cambios
    BEFORE UPDATE OR DELETE ON cambio_usuario
    FOR EACH ROW EXECUTE FUNCTION rechazar_cambios_bitacora_usuarios();

CREATE TRIGGER bitacora_usuarios_sin_truncate
    BEFORE TRUNCATE ON cambio_usuario
    FOR EACH STATEMENT EXECUTE FUNCTION rechazar_cambios_bitacora_usuarios();

-- Privilegios mínimos (T-14): un usuario nunca se borra (se desactiva), y de él la
-- aplicación solo puede cambiar el rol, si está activo y el hash de la clave.
REVOKE DELETE, UPDATE ON usuario FROM app_cientifico;
GRANT UPDATE (rol, activo, clave_hash) ON usuario TO app_cientifico;
