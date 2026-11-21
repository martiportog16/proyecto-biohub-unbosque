-- =====================================================================
-- V2 · Usuarios y roles (paquete 02, Martin) · P-28, P-25, C-2
-- Cada usuario tiene UN solo rol (P-25). El visitante no inicia sesión.
-- =====================================================================

CREATE TABLE usuario (
    id          BIGSERIAL    PRIMARY KEY,
    correo      VARCHAR(120) NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL,
    clave_hash  VARCHAR(100) NOT NULL,          -- BCrypt: nunca se guarda la clave
    rol         VARCHAR(40)  NOT NULL CHECK (rol IN (
                    'INVESTIGADOR',
                    'ADMINISTRADOR_CIENTIFICO',
                    'DIRECTOR_CIENTIFICO',
                    'AUDITOR_CIENTIFICO',
                    'ALTO_RANGO',
                    'ANALISTA',
                    'ADMINISTRADOR_PLATAFORMA')),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- El grupo público y el batch no necesitan ver usuarios ni claves.
REVOKE ALL ON usuario FROM app_publico, app_batch;

-- Usuarios de demostración: uno por rol. Clave de todos: BioHub2026!
INSERT INTO usuario (correo, nombre, clave_hash, rol) VALUES
 ('investigador@biohub.co',  'Ana Investigadora',     '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'INVESTIGADOR'),
 ('admin.cientifico@biohub.co','Luis Administrador',  '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'ADMINISTRADOR_CIENTIFICO'),
 ('director@biohub.co',      'Marta Directora',       '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'DIRECTOR_CIENTIFICO'),
 ('auditor@biohub.co',       'Jorge Auditor',         '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'AUDITOR_CIENTIFICO'),
 ('altorango@biohub.co',     'Elena Alto Rango',      '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'ALTO_RANGO'),
 ('analista@biohub.co',      'Pedro Analista',        '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'ANALISTA'),
 ('admin.plataforma@biohub.co','Sofía Plataforma',    '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'ADMINISTRADOR_PLATAFORMA');
