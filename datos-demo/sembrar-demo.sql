-- =====================================================================
-- Datos de demostración para la sustentación (paquete 19).
--
-- Agrega una historia creíble con FECHAS PASADAS, que la API no permite crear
-- (todo lo que se registra por la API queda con la fecha de hoy):
--   - 4 hallazgos de junio a octubre de 2026;
--   - uno de ellos reclasificado DOS veces (julio y septiembre), para mostrar el
--     historial y la comparación por fecha (HU-07);
--   - 5 equipos de campo, uno que ha pasado por varias manos (HU-03).
--
-- Corre como dueño de la base, solo en la demo. Si ya se corrió, no hace nada.
-- Se borra con: docker compose down -v
-- =====================================================================
DO $$
DECLARE
    ana    BIGINT := (SELECT id FROM usuario WHERE correo = 'investigador@biohub.co');
    marta  BIGINT := (SELECT id FROM usuario WHERE correo = 'director@biohub.co');
    carlos BIGINT := (SELECT id FROM usuario WHERE correo = 'investigador2@biohub.co');   -- V10
    mono   BIGINT := (SELECT id FROM especie WHERE nombre_cientifico = 'Ateles hybridus');
    oso    BIGINT := (SELECT id FROM especie WHERE nombre_cientifico = 'Tremarctos ornatus');
    mirla  BIGINT := (SELECT id FROM especie WHERE nombre_cientifico = 'Turdus fuscater');
    h1 BIGINT; h2 BIGINT; h3 BIGINT; h4 BIGINT;
    camara BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM equipo_campo WHERE codigo = 'DRON-001') THEN
        RAISE NOTICE 'Los datos de demostración ya estaban: no se hizo nada.';
        RETURN;
    END IF;

    -- ---------- Hallazgos con fechas pasadas ----------
    -- H1: se registró como mono araña por una grabación; en julio se creyó que era una mirla;
    --     en septiembre el análisis espectral confirmó que sí era el mono.
    INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion, departamento, investigador_id, registrado_en)
    VALUES (mono, mono, 1, '{"comportamiento":"social","temperatura_ambiente":24.5,"grabacion_vocalizacion":"grabaciones/meta-0610.wav"}',
            '2026-06-09', 'Meta', ana, '2026-06-10 09:15-05') RETURNING id INTO h1;
    INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion, departamento, investigador_id, registrado_en)
    VALUES (oso, oso, 1, '{"ancho_huella":12.5,"muestra_adn":"M-0412","ruta_desplazamiento":[{"lat":4.6702,"lon":-73.7431},{"lat":4.6715,"lon":-73.7402}]}',
            '2026-08-02', 'Cundinamarca', ana, '2026-08-03 16:40-05') RETURNING id INTO h2;
    INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion, departamento, investigador_id, registrado_en)
    VALUES (mirla, mirla, 1, '{"numero_individuos":4,"grabacion_canto":"grabaciones/bogota-0912.wav"}',
            '2026-09-12', 'Bogotá D.C.', ana, '2026-09-12 07:05-05') RETURNING id INTO h3;
    INSERT INTO hallazgo (especie_id, ficha_especie_id, version_ficha, valores, fecha_observacion, departamento, investigador_id, registrado_en)
    VALUES (oso, oso, 1, '{"ancho_huella":13.1}',
            '2026-10-01', 'Boyacá', ana, '2026-10-01 11:30-05') RETURNING id INTO h4;

    INSERT INTO protegido.coordenada (hallazgo_id, latitud, longitud, precision_m, registrada_en) VALUES
        (h1, 3.452100, -73.751300, 15, '2026-06-10 09:15-05'),
        (h2, 4.670200, -73.743100, 8,  '2026-08-03 16:40-05'),
        (h3, 4.636200, -74.082100, 20, '2026-09-12 07:05-05'),
        (h4, 5.703400, -72.928700, 10, '2026-10-01 11:30-05');

    -- ---------- Historial de H1: dos reclasificaciones ----------
    INSERT INTO reclasificacion (hallazgo_id, especie_anterior_id, especie_nueva_id, motivo, autor_id, realizada_en) VALUES
        (h1, mono, mirla, 'Revisión de la grabación: el canto parece de Turdus fuscater, no una vocalización de primate', marta, '2026-07-15 10:20-05'),
        (h1, mirla, mono, 'Análisis espectral del laboratorio confirma vocalización de Ateles hybridus (informe LAB-2026-091)', marta, '2026-09-04 15:45-05');
    -- La especie ACTUAL del hallazgo es la del último cambio (aquí coincide con la inicial)
    UPDATE hallazgo SET especie_id = mono WHERE id = h1;

    -- ---------- Equipos de campo ----------
    INSERT INTO equipo_campo (codigo, tipo, descripcion, responsable_id, registrado_por, registrado_en) VALUES
        ('DRON-001',  'DRON',               'DJI Mavic 3 Enterprise',         ana,   ana,   '2026-05-02 08:00-05'),
        ('SENSOR-T-12','SENSOR',            'Temperatura y humedad, páramo',  carlos, carlos, '2026-05-20 09:00-05'),
        ('GRAB-02',   'GRABADORA_ACUSTICA', 'AudioMoth 1.2',                  ana,   ana,   '2026-06-01 08:30-05'),
        ('GPS-03',    'GPS',                'Garmin GPSMAP 66',               carlos, ana,   '2026-06-15 14:00-05');
    INSERT INTO equipo_campo (codigo, tipo, descripcion, responsable_id, registrado_por, registrado_en)
    VALUES ('CAM-TRAMPA-07', 'CAMARA_TRAMPA', 'Bushnell Core DS, visión nocturna', ana, ana, '2026-05-10 10:00-05')
    RETURNING id INTO camara;

    INSERT INTO asignacion_equipo (equipo_id, responsable_id, asignado_por, asignado_en, motivo)
    SELECT id, responsable_id, registrado_por, registrado_en, 'Registro del equipo' FROM equipo_campo
     WHERE codigo IN ('DRON-001', 'SENSOR-T-12', 'GRAB-02', 'GPS-03', 'CAM-TRAMPA-07');
    -- La cámara pasó a Carlos para el páramo y volvió a Ana (el responsable siempre es un investigador, HU-03)
    INSERT INTO asignacion_equipo (equipo_id, responsable_id, asignado_por, asignado_en, motivo) VALUES
        (camara, carlos, ana,    '2026-08-01 07:30-05', 'Instalación en el páramo de Chingaza durante agosto'),
        (camara, ana,    carlos, '2026-10-02 16:00-05', 'Regresa al laboratorio para mantenimiento');
    -- equipo_campo no admite UPDATE general: solo del responsable (V9)
    UPDATE equipo_campo SET responsable_id = ana WHERE id = camara;

    RAISE NOTICE 'Listo: hallazgos % (con 2 reclasificaciones), %, %, % y 5 equipos.', h1, h2, h3, h4;
END $$;
