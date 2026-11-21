-- =====================================================================
-- V1 · Base de BioHub (paquete 01)
-- Crea los usuarios de base de datos de cada rol y sus permisos por defecto.
-- Cada módulo agrega sus tablas en su propia migración:
--   V2 usuarios · V3 fichas y hallazgos · V4 historial · V5 autorización de
--   coordenadas · V6 consumidores externos · V7 informes
-- =====================================================================

-- 1. Un usuario de base de datos por rol (T-14: privilegios mínimos).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_cientifico') THEN
        CREATE ROLE app_cientifico LOGIN PASSWORD '${clave_app_cientifico}';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_publico') THEN
        CREATE ROLE app_publico LOGIN PASSWORD '${clave_app_publico}';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_batch') THEN
        CREATE ROLE app_batch LOGIN PASSWORD '${clave_app_batch}';
    END IF;
END
$$;

-- 2. Esquema "protegido" para las coordenadas exactas (ADR-06, T-16).
--    Solo el rol científico puede entrar. El público y el batch no (PR-09).
CREATE SCHEMA IF NOT EXISTS protegido;
REVOKE ALL ON SCHEMA protegido FROM PUBLIC;
GRANT USAGE ON SCHEMA protegido TO app_cientifico;

-- 3. Permisos por defecto en el esquema público.
--    Toda tabla nueva que cree el dueño (Flyway) hereda estos permisos.
GRANT USAGE ON SCHEMA public TO app_cientifico, app_publico, app_batch;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_cientifico;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_cientifico;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO app_publico, app_batch;

-- En "protegido" el rol científico solo puede leer e insertar: las coordenadas
-- no se editan ni se borran desde la aplicación.
ALTER DEFAULT PRIVILEGES IN SCHEMA protegido
    GRANT SELECT, INSERT ON TABLES TO app_cientifico;
ALTER DEFAULT PRIVILEGES IN SCHEMA protegido
    GRANT USAGE, SELECT ON SEQUENCES TO app_cientifico;

-- 4. Las tablas que una migración posterior quiera excluir de un rol
--    (por ejemplo, el historial sin UPDATE ni DELETE) hacen su propio REVOKE.
