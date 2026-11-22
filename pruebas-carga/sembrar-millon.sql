-- =====================================================================
-- Siembra de datos para las pruebas de carga (paquete 13, Sergio) · PR-13, RNF-09
--
-- Crea 40 especies de prueba y 1 000 000 de hallazgos repartidos en:
--   - los 32 departamentos y Bogotá,
--   - los 24 meses anteriores al mes actual,
-- cada uno con su coordenada exacta en el esquema protegido.
--
-- Solo para pruebas: se corre como DUEÑO de la base, con scripts/sembrar-millon.sh.
-- Para quitar todo: docker compose down -v (borra la base completa).
-- La cantidad se puede cambiar con: psql -v total=200000 ...
-- =====================================================================
\set ON_ERROR_STOP on
\if :{?total}
\else
  \set total 1000000
\endif
\timing on

BEGIN;

-- 1. 40 especies de prueba, cada una con una ficha sencilla (un atributo NUMERO)
INSERT INTO especie (nombre_cientifico, nombre_comun, en_peligro)
SELECT 'Especie sembrada ' || lpad(n::text, 2, '0'), 'Especie de carga ' || n, n % 10 = 0
  FROM generate_series(1, 40) AS n
ON CONFLICT (nombre_cientifico) DO NOTHING;

INSERT INTO definicion_ficha (especie_id, version, atributos, creada_por)
SELECT e.id, 1,
       '[{"nombre":"conteo","tipo":"NUMERO","obligatorio":true,"activo":true,"opciones":[],"unidad":null}]'::jsonb,
       (SELECT id FROM usuario WHERE correo = 'admin.cientifico@biohub.co')
  FROM especie e
 WHERE e.nombre_cientifico LIKE 'Especie sembrada %'
   AND NOT EXISTS (SELECT 1 FROM definicion_ficha d WHERE d.especie_id = e.id);

-- 2. Los hallazgos. Cada departamento tiene su propia "riqueza": unos ven muchas
--    especies y otros pocas, para que el índice de presión dé valores variados.
CREATE TEMP TABLE semilla_especies AS
SELECT row_number() OVER (ORDER BY id) - 1 AS n, id FROM especie WHERE nombre_cientifico LIKE 'Especie sembrada %';

CREATE TEMP TABLE semilla_departamentos (n int, nombre text);
INSERT INTO semilla_departamentos
SELECT row_number() OVER () - 1, d FROM unnest(ARRAY[
 'Amazonas','Antioquia','Arauca','Atlántico','Bogotá D.C.','Bolívar','Boyacá','Caldas','Caquetá','Casanare',
 'Cauca','Cesar','Chocó','Córdoba','Cundinamarca','Guainía','Guaviare','Huila','La Guajira','Magdalena','Meta',
 'Nariño','Norte de Santander','Putumayo','Quindío','Risaralda','San Andrés y Providencia','Santander','Sucre',
 'Tolima','Valle del Cauca','Vaupés','Vichada']) AS d;

CREATE TEMP TABLE semilla AS
SELECT g AS n,
       (g % 33)                                                   AS depto,
       -- cada departamento usa solo las primeras (10 + depto) especies, con algo de azar
       (abs(hashint4(g)) % (10 + (g % 33) % 30))                  AS especie,
       (date_trunc('month', now()) - interval '24 months')::date
           + (abs(hashint4(g * 7)) % 730)                         AS fecha
  FROM generate_series(1, :total) AS g;

INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion, departamento,
                      investigador_id, registrado_en)
SELECT e.id, e.id, 1, jsonb_build_object('conteo', 1 + s.n % 20), s.fecha, d.nombre,
       (SELECT id FROM usuario WHERE correo = 'investigador@biohub.co'), s.fecha + time '12:00'
  FROM semilla s
  JOIN semilla_especies e ON e.n = s.especie % 40
  JOIN semilla_departamentos d ON d.n = s.depto;

-- 3. Una coordenada exacta por cada hallazgo sembrado (dentro de Colombia)
INSERT INTO protegido.coordenada (hallazgo_id, latitud, longitud, precision_m)
SELECT h.id, -4 + (abs(hashint4(h.id::int)) % 1600) / 100.0, -79 + (abs(hashint4(h.id::int * 3)) % 1200) / 100.0, 10
  FROM hallazgo h
 WHERE h.especie_id IN (SELECT id FROM semilla_especies)
   AND NOT EXISTS (SELECT 1 FROM protegido.coordenada c WHERE c.hallazgo_id = h.id);

COMMIT;

-- 4. Estadísticas al día para que PostgreSQL planee bien las consultas
ANALYZE especie;
ANALYZE hallazgo;
ANALYZE protegido.coordenada;

SELECT count(*) AS hallazgos_totales, min(fecha_observacion) AS desde, max(fecha_observacion) AS hasta FROM hallazgo;
