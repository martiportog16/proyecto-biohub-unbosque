-- =====================================================================
-- V7 · Informes mensuales de presión (paquete 10a, Daniel) · HU-11, RF-14, C-1, P-03
--
-- El batch LEE los hallazgos de la réplica de analítica (lo pesado) y GUARDA aquí,
-- en la primaria, solo el resultado: unas pocas filas por mes, en una transacción.
-- =====================================================================

CREATE TABLE informe_mensual (
    id                  BIGSERIAL   PRIMARY KEY,
    periodo             DATE        NOT NULL UNIQUE CHECK (extract(day FROM periodo) = 1),  -- primer día del mes
    generado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    total_departamentos INT         NOT NULL
);

CREATE TABLE indice_presion (
    informe_id    BIGINT       NOT NULL REFERENCES informe_mensual(id),
    departamento  VARCHAR(60)  NOT NULL,
    valor         NUMERIC(5,4) CHECK (valor BETWEEN 0 AND 1),   -- NULL = sin datos
    especies_mes  INT          NOT NULL,
    base          NUMERIC(8,4),
    PRIMARY KEY (informe_id, departamento)
);

-- El analista pide un informe desde la API (grupo científico); el batch la atiende (P-11).
CREATE TABLE solicitud_informe (
    id             BIGSERIAL   PRIMARY KEY,
    periodo        DATE        NOT NULL CHECK (extract(day FROM periodo) = 1),
    solicitado_por BIGINT      NOT NULL REFERENCES usuario(id),
    solicitado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    atendida_en    TIMESTAMPTZ
);

-- Un informe generado no se edita ni se borra.
REVOKE UPDATE, DELETE ON informe_mensual, indice_presion FROM app_cientifico;

-- El batch escribe SOLO el resultado (privilegios mínimos, T-14).
GRANT INSERT ON informe_mensual, indice_presion TO app_batch;
GRANT SELECT, UPDATE (atendida_en) ON solicitud_informe TO app_batch;
GRANT USAGE, SELECT ON SEQUENCE informe_mensual_id_seq TO app_batch;
