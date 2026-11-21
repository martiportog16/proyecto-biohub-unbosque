-- =====================================================================
-- V3_1 · Hallazgos y coordenadas (paquete 04, Daniel) · HU-01, RF-01, C-3
--
-- El hallazgo guarda la versión de la ficha con la que se validó: si la ficha
-- cambia después, el hallazgo no se ve afectado (RNF-01, "0 registros afectados").
-- Las coordenadas exactas van APARTE, en el esquema "protegido" (ADR-06, T-16).
-- =====================================================================

CREATE TABLE hallazgo (
    id                BIGSERIAL    PRIMARY KEY,
    especie_id        BIGINT       NOT NULL REFERENCES especie(id),   -- clasificación ACTUAL (cambia al reclasificar)
    ficha_especie_id  BIGINT       NOT NULL,                          -- ficha con la que se validaron los valores
    version_ficha     INT          NOT NULL,
    valores           JSONB        NOT NULL,
    fecha_observacion DATE         NOT NULL,
    departamento      VARCHAR(60)  NOT NULL,                          -- lo único de ubicación que ve el público
    investigador_id   BIGINT       NOT NULL REFERENCES usuario(id),
    registrado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    FOREIGN KEY (ficha_especie_id, version_ficha) REFERENCES definicion_ficha (especie_id, version)
);

CREATE INDEX idx_hallazgo_especie ON hallazgo (especie_id);
-- Para el informe mensual: especies por departamento y mes (HU-11)
CREATE INDEX idx_hallazgo_departamento_fecha ON hallazgo (departamento, fecha_observacion);

-- Un hallazgo nunca se borra desde la aplicación.
REVOKE DELETE ON hallazgo FROM app_cientifico;

-- Coordenadas exactas: solo las escribe el registro (C-3) y solo las lee el
-- componente de autorización (paquete 07). El grupo público y el batch no tienen
-- permiso sobre el esquema "protegido" (V1).
CREATE TABLE protegido.coordenada (
    hallazgo_id   BIGINT       PRIMARY KEY REFERENCES public.hallazgo(id),
    latitud       NUMERIC(9,6) NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud      NUMERIC(9,6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    precision_m   INT          CHECK (precision_m > 0),
    registrada_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
