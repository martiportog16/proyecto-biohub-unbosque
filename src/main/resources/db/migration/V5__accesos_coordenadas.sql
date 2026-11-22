-- =====================================================================
-- V5 · Registro de accesos a coordenadas (paquete 07, Martin) · RNF-06, P-18, HU-08
--
-- Cada consulta de coordenadas exactas deja una fila aquí, se haya PERMITIDO o
-- NEGADO: quién, con qué rol, qué pidió, cuándo y por qué se decidió así.
-- Es la evidencia de "0 accesos exitosos de roles no autorizados".
--
-- Vive en el esquema "protegido" (V1), así que:
--   - app_cientifico solo puede LEER e INSERTAR (no editar ni borrar);
--   - app_publico y app_batch no pueden ni entrar al esquema.
-- =====================================================================

CREATE TABLE protegido.acceso_coordenadas (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES public.usuario(id),
    rol         TEXT         NOT NULL,                       -- el rol que tenía en ese momento
    especie_id  BIGINT       NOT NULL REFERENCES public.especie(id),
    hallazgo_id BIGINT       REFERENCES public.hallazgo(id), -- NULL = pidió el mapa de toda la especie
    en_peligro  BOOLEAN      NOT NULL,                       -- si la especie estaba en peligro en ese momento
    permitido   BOOLEAN      NOT NULL,
    motivo      TEXT         NOT NULL,
    ocurrido_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- El auditor consulta lo más reciente primero, y los intentos negados.
CREATE INDEX idx_acceso_coordenadas_fecha ON protegido.acceso_coordenadas (ocurrido_en DESC, id DESC);
CREATE INDEX idx_acceso_coordenadas_negados ON protegido.acceso_coordenadas (ocurrido_en DESC) WHERE NOT permitido;
