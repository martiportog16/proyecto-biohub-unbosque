-- =====================================================================
-- V8 · Grupos taxonómicos y fichas por grupo (paquete 14, Daniel) · RF-02, HU-02
--
-- RF-02 y HU-02 piden fichas "por especie o categoría". El diagrama de clases del
-- Entregable 2 lo dibuja así: FichaTaxonomica define para una Especie O para un
-- GrupoTaxonomico, y cada Especie pertenece a un grupo.
--
-- Cómo funciona:
--   - Un grupo puede tener su propia ficha, versionada igual que la de una especie.
--   - Las especies del grupo que la heredan reciben una COPIA, como versión nueva de
--     su propia ficha. Así cada hallazgo sigue apuntando a una versión fija de la ficha
--     de su especie (PR-01, "0 registros afectados") sin cambiar el registro.
--   - Cambiar la ficha de un grupo no toca las especies de otros grupos (HU-02, crit. 4).
-- =====================================================================

CREATE TABLE grupo_taxonomico (
    id          BIGSERIAL     PRIMARY KEY,
    nombre      VARCHAR(100)  NOT NULL UNIQUE,
    descripcion TEXT
);

-- La ficha de un grupo: igual que definicion_ficha, pero su dueño es el grupo
CREATE TABLE definicion_ficha_grupo (
    id          BIGSERIAL    PRIMARY KEY,
    grupo_id    BIGINT       NOT NULL REFERENCES grupo_taxonomico(id),
    version     INT          NOT NULL CHECK (version >= 1),
    atributos   JSONB        NOT NULL,
    creada_por  BIGINT       NOT NULL REFERENCES usuario(id),
    creada_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (grupo_id, version)
);
-- Una versión de ficha nunca se edita ni se borra (igual que en V3)
REVOKE UPDATE, DELETE ON definicion_ficha_grupo FROM app_cientifico;

-- A qué grupo pertenece cada especie y si hereda la ficha del grupo
ALTER TABLE especie
    ADD COLUMN grupo_id     BIGINT  REFERENCES grupo_taxonomico(id),
    ADD COLUMN hereda_ficha BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT hereda_solo_con_grupo CHECK (NOT hereda_ficha OR grupo_id IS NOT NULL);

-- Rastro: de qué versión del grupo salió cada versión copiada a una especie
ALTER TABLE definicion_ficha
    ADD COLUMN grupo_origen_id      BIGINT,
    ADD COLUMN version_grupo_origen INT,
    ADD CONSTRAINT ficha_copiada_de_grupo
        FOREIGN KEY (grupo_origen_id, version_grupo_origen)
        REFERENCES definicion_ficha_grupo (grupo_id, version);

CREATE INDEX idx_especie_grupo ON especie (grupo_id) WHERE grupo_id IS NOT NULL;

-- Grupos de ejemplo. Las 3 especies de V3 quedan en su grupo, pero con su ficha
-- propia (no heredan), así nada de lo que ya existía cambia.
INSERT INTO grupo_taxonomico (nombre, descripcion) VALUES
    ('Primates',  'Monos y otros primates'),
    ('Úrsidos',   'Osos'),
    ('Aves paseriformes', 'Aves cantoras como mirlas y toches');

UPDATE especie SET grupo_id = (SELECT id FROM grupo_taxonomico WHERE nombre = 'Primates')
 WHERE nombre_cientifico = 'Ateles hybridus';
UPDATE especie SET grupo_id = (SELECT id FROM grupo_taxonomico WHERE nombre = 'Úrsidos')
 WHERE nombre_cientifico = 'Tremarctos ornatus';
UPDATE especie SET grupo_id = (SELECT id FROM grupo_taxonomico WHERE nombre = 'Aves paseriformes')
 WHERE nombre_cientifico = 'Turdus fuscater';
