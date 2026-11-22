-- =====================================================================
-- V9 · Equipos de campo (paquete 15, Daniel) · RF-03, RF-04, HU-03, CU-03, CU-04, secuencia 6
--
-- Cada equipo (dron, cámara trampa, sensor...) tiene SIEMPRE un responsable:
-- responsable_id es NOT NULL (HU-03, criterio 4). La regla está en el dominio
-- (EquipoCampo.crear) y además aquí, por si algo se saltara la aplicación.
--
-- asignacion_equipo es el historial: cada vez que un equipo cambia de manos se
-- agrega una fila. Nunca se edita ni se borra (trazabilidad de los activos).
-- =====================================================================

CREATE TABLE equipo_campo (
    id              BIGSERIAL    PRIMARY KEY,
    codigo          VARCHAR(40)  NOT NULL UNIQUE CHECK (codigo ~ '^[A-Z0-9-]{3,40}$'),
    tipo            VARCHAR(30)  NOT NULL CHECK (tipo IN
                        ('DRON', 'CAMARA_TRAMPA', 'SENSOR', 'GPS', 'GRABADORA_ACUSTICA', 'OTRO')),
    descripcion     TEXT,
    responsable_id  BIGINT       NOT NULL REFERENCES usuario(id),
    registrado_por  BIGINT       NOT NULL REFERENCES usuario(id),
    registrado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_equipo_responsable ON equipo_campo (responsable_id);

CREATE TABLE asignacion_equipo (
    id              BIGSERIAL    PRIMARY KEY,
    equipo_id       BIGINT       NOT NULL REFERENCES equipo_campo(id),
    responsable_id  BIGINT       NOT NULL REFERENCES usuario(id),
    asignado_por    BIGINT       NOT NULL REFERENCES usuario(id),
    asignado_en     TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    motivo          TEXT         NOT NULL
);
CREATE INDEX idx_asignacion_equipo ON asignacion_equipo (equipo_id, asignado_en);

-- Permisos (privilegios mínimos, T-14):
--  - un equipo no se borra, y de él solo puede cambiar el responsable;
--  - el historial solo crece;
--  - el portal y el batch no tienen por qué saber quién tiene cada equipo.
REVOKE UPDATE, DELETE ON equipo_campo FROM app_cientifico;
GRANT UPDATE (responsable_id) ON equipo_campo TO app_cientifico;
REVOKE UPDATE, DELETE ON asignacion_equipo FROM app_cientifico;
REVOKE ALL ON equipo_campo, asignacion_equipo FROM app_publico, app_batch;
