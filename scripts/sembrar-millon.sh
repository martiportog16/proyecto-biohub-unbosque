#!/usr/bin/env bash
# =====================================================================
# Siembra 1 millón de hallazgos para las pruebas de carga (PR-13).
# Tarda de 1 a 3 minutos. Para borrarlos: docker compose down -v
#
# Uso:   ./scripts/sembrar-millon.sh            # 1 000 000
#        TOTAL=200000 ./scripts/sembrar-millon.sh
# =====================================================================
set -eu
cd "$(dirname "$0")/.."

valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PRIMARIA="$(valor_env DB_HOST_PRIMARIA)"; PRIMARIA="${PRIMARIA:-postgres-primaria}"
TOTAL="${TOTAL:-1000000}"

echo "Sembrando $TOTAL hallazgos en $PRIMARIA..."
docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -v total="$TOTAL" < pruebas-carga/sembrar-millon.sql
echo "Listo. Las réplicas reciben los datos en unos segundos."
