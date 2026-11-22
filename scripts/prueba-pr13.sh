#!/usr/bin/env bash
# =====================================================================
# PR-13 (RNF-09, HU-11 criterio 3): con 1 millón de hallazgos, el p95 del
# registro con el batch trabajando sube como máximo 1,0 s frente a sin batch.
#
# Fases:
#   1. SIN batch: mide el registro (k6) durante DURACION.
#   2. CON batch: mide lo mismo mientras
#        a) el analista pide los informes de los últimos 22 meses (el batch real los calcula), y
#        b) la consulta pesada del informe (recorrer TODOS los hallazgos) corre sin parar
#           en la réplica de analítica. Es el PEOR caso: un batch que nunca termina.
#   3. Compara los dos p95 y dice si cumple.
#
# Antes: ./scripts/sembrar-millon.sh
# Uso:   ./scripts/prueba-pr13.sh                 # 2 minutos por fase
#        DURACION=1m ./scripts/prueba-pr13.sh     # ensayo corto
# =====================================================================
set -u
cd "$(dirname "$0")/.."
export DURACION="${DURACION:-2m}"
valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PRIMARIA="$(valor_env DB_HOST_PRIMARIA)"; PRIMARIA="${PRIMARIA:-postgres-primaria}"
PUERTO="${PUERTO_BIOHUB:-$(valor_env PUERTO_BIOHUB)}"; PUERTO="${PUERTO:-8080}"
URL="http://localhost:$PUERTO"

p95_de() {  # archivo de resumen de k6 -> p95 en ms
    [ -f "$1" ] || return 0
    tr -d ' \n\r\t' < "$1" | grep -o '"http_req_duration":{[^}]*}' | grep -o '"p(95)":[0-9.]*' | cut -d: -f2
}
ultimo_resumen() { ls -t pruebas-carga/resultados/"$1"-*-resumen.json 2>/dev/null | head -n 1; }

TOTAL="$(docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -Atc 'SELECT count(*) FROM hallazgo' | tr -d '\r')"
echo "Hallazgos en la base: $TOTAL"
[ "${TOTAL:-0}" -ge 1000000 ] || echo "AVISO: PR-13 pide 1 millón. Corra antes ./scripts/sembrar-millon.sh"

echo
echo "=============== Fase 1: registro SIN batch ==============="
docker compose stop batch > /dev/null 2>&1
ETIQUETA=pr13-sin-batch ./scripts/carga.sh registro > /dev/null || echo "AVISO: hubo errores en el registro (ver pruebas-carga/resultados/)" 
SIN="$(p95_de "$(ultimo_resumen pr13-sin-batch)")"
echo "p95 del registro sin batch: ${SIN} ms"

echo
echo "=============== Fase 2: registro CON batch ==============="
docker compose start batch > /dev/null 2>&1
# a) El analista pide los informes de los 22 meses anteriores al pasado (el batch real los calcula)
TOKEN="$(curl -s -X POST "$URL/api/cientifico/auth/login" -H 'Content-Type: application/json' \
    -d '{"correo":"analista@biohub.co","clave":"BioHub2026!"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
PEDIDOS=0
for atras in $(seq 2 23); do
    MES="$(docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -Atc \
        "SELECT to_char(date_trunc('month', now()) - interval '$atras months', 'YYYY-MM')" | tr -d '\r')"
    C="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/api/cientifico/informes/solicitudes" \
        -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"periodo\":\"$MES\"}")"
    { [ "$C" = "202" ] || [ "$C" = "200" ]; } && PEDIDOS=$((PEDIDOS + 1))   # 200 = ese informe ya existía
done
echo "Informes pedidos al batch: $PEDIDOS (los que ya existían no se recalculan)"
# b) Peor caso: la consulta pesada del informe, sobre TODOS los hallazgos, sin parar en la réplica de analítica
CONSULTAS=pruebas-carga/resultados/pr13-consultas-pesadas.txt
: > "$CONSULTAS"
( while true; do
    docker compose exec -T postgres-replica-analitica psql -U "$DUENO" -d biohub -Atc \
      "SELECT count(*) FROM (SELECT departamento, date_trunc('month', fecha_observacion), count(DISTINCT especie_id)
         FROM hallazgo GROUP BY 1, 2) t" > /dev/null 2>&1 && echo x >> "$CONSULTAS"
  done ) &
CARGA_BATCH=$!
trap 'kill $CARGA_BATCH 2>/dev/null' EXIT

ETIQUETA=pr13-con-batch ./scripts/carga.sh registro > /dev/null || echo "AVISO: hubo errores en el registro (ver pruebas-carga/resultados/)" 
kill $CARGA_BATCH 2>/dev/null
CON="$(p95_de "$(ultimo_resumen pr13-con-batch)")"
ATENDIDOS="$(docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -Atc \
    "SELECT count(*) FROM informe_mensual" | tr -d '\r')"
echo "p95 del registro con batch: ${CON} ms"

if [ -z "$SIN" ] || [ -z "$CON" ]; then
    echo "ERROR: no se pudo leer el p95 de alguna fase. Revise pruebas-carga/resultados/"
    exit 2
fi
DIFERENCIA="$(awk -v a="$CON" -v b="$SIN" 'BEGIN { printf "%.1f", a - b }')"
CUMPLE="$(awk -v d="$DIFERENCIA" 'BEGIN { print (d <= 1000) ? "si" : "no" }')"

echo
echo "================ Resultado PR-13 ================"
echo "Hallazgos en la base:                $TOTAL"
echo "p95 del registro SIN batch:          ${SIN} ms"
echo "p95 del registro CON batch:          ${CON} ms"
echo "Diferencia:                          ${DIFERENCIA} ms   (límite: 1000 ms)"
echo "Consultas pesadas durante la medición: $(wc -l < "$CONSULTAS" | tr -d ' ') (cada una recorre todos los hallazgos)"
echo "Informes guardados por el batch:     $ATENDIDOS"
echo "================================================="
if [ "$CUMPLE" = "si" ]; then echo "PR-13 CUMPLE"; exit 0; fi
echo "PR-13 NO CUMPLE"
exit 1
