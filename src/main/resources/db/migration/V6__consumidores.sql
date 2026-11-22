-- =====================================================================
-- V6 · Consumidores externos (paquete 09a, Martin) · RF-13, P-10, P-31, T-19
-- Universidades y ONG que consumen /api/v1. Cada uno tiene su API key y su
-- límite de peticiones por minuto. De la API key solo se guarda su huella SHA-256.
-- =====================================================================

CREATE TABLE consumidor (
    id                 BIGSERIAL    PRIMARY KEY,
    nombre             VARCHAR(120) NOT NULL UNIQUE,
    api_key_hash       CHAR(64)     NOT NULL UNIQUE,     -- SHA-256 en hexadecimal
    limite_por_minuto  INT          NOT NULL CHECK (limite_por_minuto > 0),
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Consumidores de demostración:
--   API key "demo-unal-2026" -> 60 peticiones por minuto
--   API key "demo-ong-2026"  ->  5 peticiones por minuto (para mostrar el 429 en la demo)
INSERT INTO consumidor (nombre, api_key_hash, limite_por_minuto) VALUES
 ('Universidad Nacional (demo)', '79b9aadde2c83f3de253ad51711797f04f6dfa25895caa87680cf25b89033718', 60),
 ('ONG Bosques Vivos (demo)',    'f1d02d9b5ba72148eb641d399ef4e35fae3da239709bb6bcf73ee5ae403801bb', 5);
