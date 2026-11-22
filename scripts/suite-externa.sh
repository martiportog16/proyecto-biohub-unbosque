#!/usr/bin/env bash
# =====================================================================
# Suite de la API externa /v1, vista DESDE AFUERA, como la usaría una
# universidad u ONG (HU-09, PR-11). Solo usa curl contra Nginx.
#
# Es el "contrato": si estas revisiones pasan, un consumidor externo
# no nota ninguna diferencia. La usa también prueba-portabilidad.sh (PR-12).
#
# Uso (con el sistema arriba):   ./scripts/suite-externa.sh
# =====================================================================
set -u
cd "$(dirname "$0")/.."

valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2-; }
PUERTO="${PUERTO_BIOHUB:-$(valor_env PUERTO_BIOHUB)}"; PUERTO="${PUERTO:-8080}"
URL="${BIOHUB_URL:-http://localhost:$PUERTO}"
UNAL=demo-unal-2026
ONG=demo-ong-2026
FALLAS=0

revisar() {  # descripción, código esperado, código obtenido
    if [ "$2" = "$3" ]; then echo "  [OK]    $1 -> $3"; else echo "  [FALLA] $1 -> $3 (se esperaba $2)"; FALLAS=$((FALLAS + 1)); fi
}
codigo() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@"; }
cuerpo() { curl -s --max-time 10 "$@"; }

echo "Suite externa contra $URL"

revisar "Sin API key"                         401 "$(codigo "$URL/api/v1/estado")"
revisar "API key inventada"                   401 "$(codigo -H 'X-API-Key: no-existe' "$URL/api/v1/estado")"
revisar "Estado con API key"                  200 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/estado")"
revisar "Catálogo de especies"                200 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/especies")"
revisar "Inventario, primera página"          200 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/hallazgos")"
revisar "Inventario con filtro"               200 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/hallazgos?departamento=Meta")"
revisar "Parámetro inválido"                  422 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/hallazgos?despuesDe=-1")"

# El contrato: los campos que espera un consumidor siguen existiendo
PAGINA="$(cuerpo -H "X-API-Key: $UNAL" "$URL/api/v1/hallazgos")"
for campo in '"hallazgos"' '"tamanoPagina"' '"siguiente"'; do
    if echo "$PAGINA" | grep -q "$campo"; then echo "  [OK]    El inventario trae el campo $campo"
    else echo "  [FALLA] El inventario no trae el campo $campo"; FALLAS=$((FALLAS + 1)); fi
done
if echo "$PAGINA" | grep -qi -E 'latitud|coordenada|investigador'; then
    echo "  [FALLA] El inventario trae datos que los externos no deben ver"; FALLAS=$((FALLAS + 1))
else
    echo "  [OK]    El inventario no trae coordenadas ni datos del investigador"
fi

# PR-11: la ONG (límite 5) se pasa y recibe 429 con Retry-After; la universidad sigue
ULTIMO=""
for _ in 1 2 3 4 5 6 7; do ULTIMO="$(codigo -H "X-API-Key: $ONG" "$URL/api/v1/estado")"; done
revisar "PR-11: la ONG se pasa de su límite"  429 "$ULTIMO"
if curl -s -D - -o /dev/null -H "X-API-Key: $ONG" "$URL/api/v1/estado" | grep -qi '^retry-after:'; then
    echo "  [OK]    PR-11: el 429 trae Retry-After"
else
    echo "  [FALLA] PR-11: el 429 no trae Retry-After"; FALLAS=$((FALLAS + 1))
fi
revisar "PR-11: la universidad sigue"         200 "$(codigo -H "X-API-Key: $UNAL" "$URL/api/v1/estado")"

echo
if [ "$FALLAS" -eq 0 ]; then echo "Suite externa: TODO PASA"; exit 0; fi
echo "Suite externa: $FALLAS revisiones fallaron"
exit 1
