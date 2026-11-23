-- =====================================================================
-- V10 · Segundo investigador de demostración (paquete 26, Paula) · HU-03
--
-- HU-03, criterio 1: "Se selecciona un equipo y un INVESTIGADOR responsable".
-- Desde el paquete 26 el responsable de un equipo debe ser un investigador activo,
-- así que hace falta un segundo investigador para poder pasarle un equipo a otra
-- persona en la demostración y en las pruebas.
-- Clave: BioHub2026! (la misma de los demás usuarios de demostración, V2).
-- =====================================================================

INSERT INTO usuario (correo, nombre, clave_hash, rol) VALUES
('investigador2@biohub.co', 'Carlos Investigador',
 '$2a$10$oCOUBIox9.spagV.oZKvWetr5tBheTSGcfo630myulWEYm72cU0n.', 'INVESTIGADOR')
ON CONFLICT (correo) DO NOTHING;
