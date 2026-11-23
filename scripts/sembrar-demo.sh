#!/usr/bin/env bash
# =====================================================================
# Datos de demostración para la sustentación: hallazgos con fechas pasadas,
# un hallazgo reclasificado dos veces y equipos con historial.
# Ver datos-demo/sembrar-demo.sql. Correrlo dos veces no duplica nada.
#
# Uso (con BioHub corriendo):   ./scripts/sembrar-demo.sh
# =====================================================================
set -eu
cd "$(dirname "$0")/.."
valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PRIMARIA="${DB_HOST_PRIMARIA:-$(valor_env DB_HOST_PRIMARIA)}"; PRIMARIA="${PRIMARIA:-postgres-primaria}"

docker compose exec -T "$PRIMARIA" psql -q -U "$DUENO" -d biohub -v ON_ERROR_STOP=1 < datos-demo/sembrar-demo.sql
# La caché del portal puede tener los conteos viejos: vence sola en 3 s
echo "Listo. Abra la consola: http://localhost:8080/consola/ (director@biohub.co, Historial y reclasificación)"
