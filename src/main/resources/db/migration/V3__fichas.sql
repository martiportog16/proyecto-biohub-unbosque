-- =====================================================================
-- V3 · Especies y fichas flexibles (paquete 03, Daniel) · RNF-01, P-09, ADR-07
-- La ficha se guarda como DATOS (JSONB) y versionada: agregar o retirar un
-- atributo inserta una versión nueva, nunca cambia el esquema ni el código.
-- =====================================================================

CREATE TABLE especie (
    id                BIGSERIAL    PRIMARY KEY,
    nombre_cientifico VARCHAR(150) NOT NULL UNIQUE,
    nombre_comun      VARCHAR(150) NOT NULL,
    en_peligro        BOOLEAN      NOT NULL DEFAULT FALSE,   -- decide quién ve coordenadas (P-24)
    creada_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE definicion_ficha (
    id          BIGSERIAL   PRIMARY KEY,
    especie_id  BIGINT      NOT NULL REFERENCES especie(id),
    version     INT         NOT NULL CHECK (version >= 1),
    atributos   JSONB       NOT NULL,     -- lista de {nombre, tipo, obligatorio, activo, opciones, unidad}
    creada_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    creada_por  BIGINT      REFERENCES usuario(id),
    UNIQUE (especie_id, version)
);

-- Las versiones de una ficha no se editan: cada cambio es una fila nueva.
REVOKE UPDATE, DELETE ON definicion_ficha FROM app_cientifico;

-- ---------------------------------------------------------------------
-- Datos de demostración: 3 especies con su ficha versión 1
-- ---------------------------------------------------------------------
INSERT INTO especie (nombre_cientifico, nombre_comun, en_peligro) VALUES
 ('Ateles hybridus',   'Mono araña café', TRUE),
 ('Tremarctos ornatus','Oso andino',      TRUE),
 ('Turdus fuscater',   'Mirla grande',    FALSE);

INSERT INTO definicion_ficha (especie_id, version, atributos, creada_por)
SELECT e.id, 1, f.atributos::jsonb, (SELECT id FROM usuario WHERE correo = 'admin.cientifico@biohub.co')
FROM especie e
JOIN (VALUES
 ('Ateles hybridus', '[
   {"nombre":"comportamiento","tipo":"LISTA","obligatorio":true,"activo":true,"opciones":["alimentacion","desplazamiento","descanso","social"],"unidad":null},
   {"nombre":"temperatura_ambiente","tipo":"NUMERO","obligatorio":false,"activo":true,"opciones":[],"unidad":"°C"},
   {"nombre":"grabacion_vocalizacion","tipo":"ARCHIVO","obligatorio":false,"activo":true,"opciones":[],"unidad":null}]'),
 ('Tremarctos ornatus', '[
   {"nombre":"ancho_huella","tipo":"NUMERO","obligatorio":true,"activo":true,"opciones":[],"unidad":"cm"},
   {"nombre":"ruta_desplazamiento","tipo":"RUTA_GPS","obligatorio":false,"activo":true,"opciones":[],"unidad":null},
   {"nombre":"muestra_adn","tipo":"TEXTO","obligatorio":false,"activo":true,"opciones":[],"unidad":null}]'),
 ('Turdus fuscater', '[
   {"nombre":"numero_individuos","tipo":"NUMERO","obligatorio":true,"activo":true,"opciones":[],"unidad":null},
   {"nombre":"grabacion_canto","tipo":"ARCHIVO","obligatorio":false,"activo":true,"opciones":[],"unidad":null}]')
) AS f(nombre, atributos) ON f.nombre = e.nombre_cientifico;
