#!/usr/bin/env bash
# =====================================================================
# PR-04 (RNF-04, HU-05): se apaga una instancia científica MIENTRAS se
# registran hallazgos, y el registro no debe tener errores.
#
# Qué hace:
#   1. Inicia sesión como investigador y registra hallazgos sin parar
#      desde varios "trabajadores" a la vez.
#   2. A los pocos segundos apaga UNA de las dos instancias científicas.
#   3. Cuenta cuántos registros salieron bien (201) y cuántos fallaron.
#   4. Compara con la base de datos: deben haberse guardado exactamente los que
#      salieron bien (ni perdidos ni duplicados).
#   5. Vuelve a encender la instancia.
#
# Uso (con el sistema arriba: docker compose up -d):
#   ./scripts/prueba-caida-instancia.sh
#   MODO=abrupto ./scripts/prueba-caida-instancia.sh     # caída brusca (docker kill)
#
# Funciona en Linux, macOS, WSL y Git Bash. Necesita docker y curl.
# =====================================================================
set -u

URL="${BIOHUB_URL:-http://localhost:8080}"
DURACION="${DURACION:-30}"            # segundos registrando
TRABAJADORES="${TRABAJADORES:-4}"     # registros simultáneos
APAGAR_A_LOS="${APAGAR_A_LOS:-8}"     # segundo en que se apaga la instancia
MODO="${MODO:-ordenado}"              # ordenado = docker stop | abrupto = docker kill

cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

instancias() { docker compose ps -q --status running app-cientifico; }

# Cuántos hallazgos de esta prueba hay guardados en la base primaria
valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2-; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PRIMARIA="$(valor_env DB_HOST_PRIMARIA)"; PRIMARIA="${PRIMARIA:-postgres-primaria}"
guardados() {
    docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -Atc \
        "SELECT count(*) FROM hallazgo WHERE fecha_observacion = '2020-01-15' AND departamento = 'Cundinamarca'" | tr -d '\r'
}

if [ "$(instancias | wc -l)" -lt 2 ]; then
    echo "ERROR: se necesitan 2 instancias científicas corriendo (docker compose up -d)." >&2
    exit 2
fi

TOKEN="$(curl -s -X POST "$URL/api/cientifico/auth/login" -H 'Content-Type: application/json' \
    -d '{"correo":"investigador@biohub.co","clave":"BioHub2026!"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [ -z "$TOKEN" ]; then
    echo "ERROR: no se pudo iniciar sesión en $URL" >&2
    exit 2
fi

# Hallazgo de la especie 3 (Turdus fuscater), con una fecha antigua para no mezclarse con datos reales.
CUERPO='{"especieId":3,"fechaObservacion":"2020-01-15","departamento":"Cundinamarca","valores":{"numero_individuos":1},"coordenada":{"latitud":4.6,"longitud":-74.1}}'

ANTES="$(guardados)"
FIN=$(( $(date +%s) + DURACION ))
trabajador() {
    while [ "$(date +%s)" -lt "$FIN" ]; do
        curl -s -o /dev/null --max-time 15 -w '%{http_code} %{time_total}\n' \
            -X POST "$URL/api/cientifico/hallazgos" \
            -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$CUERPO" >> "$TMP/$1.txt"
    done
}

echo "Registrando hallazgos durante ${DURACION}s con ${TRABAJADORES} trabajadores..."
for n in $(seq 1 "$TRABAJADORES"); do trabajador "$n" & done

sleep "$APAGAR_A_LOS"
VICTIMA="$(instancias | tail -n 1)"
if [ "$MODO" = "abrupto" ]; then
    echo ">>> Segundo ${APAGAR_A_LOS}: caída BRUSCA de una instancia (docker kill)"
    docker kill "$VICTIMA" > /dev/null
else
    echo ">>> Segundo ${APAGAR_A_LOS}: se apaga una instancia (docker stop)"
    docker stop -t 30 "$VICTIMA" > /dev/null
fi
echo ">>> Instancias científicas que quedan: $(instancias | wc -l)"

wait

cat "$TMP"/*.txt > "$TMP/todo.txt"
TOTAL=$(wc -l < "$TMP/todo.txt" | tr -d ' ')
BIEN=$(grep -c '^201 ' "$TMP/todo.txt")
MAL=$(( TOTAL - BIEN ))
EN_LA_BASE=$(( $(guardados) - ANTES ))
P95=$(cut -d' ' -f2 "$TMP/todo.txt" | sort -n | awk '{v[NR]=$1} END {i=int(NR*0.95); if (i<1) i=1; print v[i]}')
MAX=$(cut -d' ' -f2 "$TMP/todo.txt" | sort -n | tail -n 1)

echo
echo "================ Resultado PR-04 ================"
echo "Modo de la caída:        $MODO"
echo "Registros intentados:    $TOTAL"
echo "Registros correctos:     $BIEN"
echo "Registros con error:     $MAL"
echo "Guardados en la base:    $EN_LA_BASE"
if [ "$MAL" -gt 0 ]; then
    echo "Errores por código HTTP (000 = sin respuesta):"
    grep -v '^201 ' "$TMP/todo.txt" | cut -d' ' -f1 | sort | uniq -c | sed 's/^/    /'
fi
echo "Tiempo de respuesta p95: ${P95}s   máximo: ${MAX}s"
echo "================================================="

echo "Volviendo a encender la instancia..."
docker compose up -d --no-deps app-cientifico > /dev/null 2>&1
# Nginx guarda las direcciones de las instancias al arrancar: se recarga para que vea la que volvió.
docker compose exec -T nginx nginx -s reload > /dev/null 2>&1

if [ "$MAL" -eq 0 ] && [ "$EN_LA_BASE" -eq "$BIEN" ]; then
    echo "PR-04 CUMPLE: 0 errores en el registro, y la base tiene exactamente los $BIEN registros."
    exit 0
fi
[ "$MAL" -gt 0 ] && echo "PR-04 NO CUMPLE: hubo $MAL registros con error."
[ "$EN_LA_BASE" -ne "$BIEN" ] && echo "PR-04 NO CUMPLE: salieron bien $BIEN registros pero la base guardó $EN_LA_BASE."
exit 1
