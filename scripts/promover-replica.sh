#!/usr/bin/env bash
# =====================================================================
# PR-05 (RNF-04, P-21): conmutación por error MANUAL.
#
# Cuándo usarlo: la base primaria se cayó y no vuelve. El registro de hallazgos
# está detenido (el portal público sigue funcionando: lee de su réplica).
#
# Qué hace, en orden:
#   1. AÍSLA la primaria vieja (la apaga) y detiene el batch.
#   2. PROMUEVE la réplica de analítica: deja de ser de solo lectura.
#   3. Hace que la réplica pública siga a la nueva primaria.
#   4. Guarda DB_HOST_PRIMARIA en .env y reinicia el grupo científico apuntando a ella.
#   5. Espera a que el registro vuelva a responder y dice cuánto tardó (MTTR).
#
# Uso:
#   ./scripts/promover-replica.sh          # pide confirmación
#   ./scripts/promover-replica.sh --si     # sin preguntar
#
# Funciona en Linux, macOS, WSL y Git Bash. Necesita docker y curl.
# Lea antes docs/guia-disponibilidad.md.
# =====================================================================
set -u
cd "$(dirname "$0")/.."

VIEJA=postgres-primaria
NUEVA=postgres-replica-analitica
PUBLICA=postgres-replica-publica

valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2-; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PUERTO="${PUERTO_BIOHUB:-$(valor_env PUERTO_BIOHUB)}"; PUERTO="${PUERTO:-8080}"
URL="${BIOHUB_URL:-http://localhost:$PUERTO}"

sql() { docker compose exec -T "$1" psql -U "$DUENO" -d biohub -Atc "$2" 2>&1; }
paso() { echo; echo "[$(( $(date +%s) - T0 ))s] $*"; }
fallar() { echo "ERROR: $*" >&2; exit 1; }

# ---------- Revisiones antes de tocar nada ----------
[ -n "$(docker compose ps -q --status running $NUEVA)" ] || fallar "la réplica de analítica no está corriendo."
EN_RECUPERACION="$(sql $NUEVA 'SELECT pg_is_in_recovery()')"
if [ "$EN_RECUPERACION" = "f" ]; then
    echo "La réplica de analítica YA es primaria: no hay nada que promover."
    exit 0
fi
[ "$EN_RECUPERACION" = "t" ] || fallar "no se pudo consultar la réplica de analítica: $EN_RECUPERACION"

echo "Último cambio que la réplica recibió de la primaria: $(sql $NUEVA 'SELECT coalesce(pg_last_xact_replay_timestamp()::text, $$ninguno$$)')"
echo "Lo que la primaria haya guardado DESPUÉS de ese momento se pierde (la replicación es asíncrona)."

if [ "${1:-}" != "--si" ]; then
    printf '\nEsto apaga la primaria actual y convierte la réplica de analítica en la nueva primaria.\nEscriba SI para continuar: '
    read -r RESPUESTA
    [ "$RESPUESTA" = "SI" ] || { echo "Cancelado."; exit 1; }
fi

T0=$(date +%s)

# ---------- 1. Aislar la primaria vieja ----------
paso "1/5 Apagando la primaria vieja y el batch"
# Si la primaria vieja reviviera, habría DOS primarias aceptando escrituras distintas.
# El batch se detiene porque ya no tiene réplica propia: no debe leer de la nueva primaria (RNF-09).
docker compose stop $VIEJA batch > /dev/null 2>&1

# ---------- 2. Promover ----------
paso "2/5 Promoviendo la réplica de analítica"
[ "$(sql $NUEVA 'SELECT pg_promote(true, 60)')" = "t" ] || fallar "la réplica no aceptó la promoción."
[ "$(sql $NUEVA 'SELECT pg_is_in_recovery()')" = "f" ] || fallar "la réplica sigue en modo solo lectura."
echo "    $NUEVA ya acepta escrituras."

# ---------- 3. La réplica pública sigue a la nueva primaria ----------
paso "3/5 Conectando la réplica pública a la nueva primaria"
sql $NUEVA "SELECT pg_create_physical_replication_slot('replica_publica') WHERE NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'replica_publica')" > /dev/null
CLAVE_REPLICADOR="$(docker compose exec -T $PUBLICA printenv CLAVE_REPLICADOR | tr -d '\r')"
sql $PUBLICA "ALTER SYSTEM SET primary_conninfo = 'host=$NUEVA port=5432 user=replicador password=$CLAVE_REPLICADOR application_name=replica_publica'" > /dev/null
sql $PUBLICA "SELECT pg_reload_conf()" > /dev/null

# ---------- 4. El grupo científico apunta a la nueva primaria ----------
paso "4/5 Reiniciando el grupo científico contra la nueva primaria"
# Queda escrito en .env para que cualquier "docker compose up" posterior lo respete.
if [ -f .env ] && grep -q '^DB_HOST_PRIMARIA=' .env; then
    grep -v '^DB_HOST_PRIMARIA=' .env > .env.tmp && mv .env.tmp .env
fi
echo "DB_HOST_PRIMARIA=$NUEVA" >> .env
docker compose up -d --no-deps app-cientifico > /dev/null 2>&1 || fallar "no se pudo reiniciar el grupo científico."
# Las instancias nuevas tienen otra dirección: Nginx debe volver a averiguarla.
docker compose exec -T nginx nginx -s reload > /dev/null 2>&1

# ---------- 5. Esperar a que el registro vuelva ----------
paso "5/5 Esperando a que el grupo científico responda"
LISTO=no
for _ in $(seq 1 90); do
    CODIGO="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST "$URL/api/cientifico/auth/login" \
        -H 'Content-Type: application/json' -d '{"correo":"investigador@biohub.co","clave":"BioHub2026!"}')"
    if [ "$CODIGO" = "200" ]; then LISTO=si; break; fi
    sleep 2
done
MTTR=$(( $(date +%s) - T0 ))
[ "$LISTO" = "si" ] || fallar "el grupo científico no respondió en 3 minutos. Revise: docker compose logs app-cientifico"

# La réplica pública tarda unos segundos en reconectarse (reintenta cada 5 s).
SIGUE=no
for _ in $(seq 1 20); do
    if [ "$(sql $NUEVA "SELECT count(*) FROM pg_stat_replication WHERE application_name = 'replica_publica' AND state = 'streaming'")" = "1" ]; then
        SIGUE=si; break
    fi
    sleep 2
done

echo
echo "================ Resultado PR-05 ================"
echo "Nueva primaria:               $NUEVA"
echo "Tiempo del script (MTTR):     ${MTTR} s  (desde aislar la primaria hasta que el registro respondió)"
if [ "$SIGUE" = "si" ]; then
    echo "Réplica pública:              siguiendo a la nueva primaria"
else
    echo "Réplica pública:              NO se conectó a la nueva primaria."
    echo "    El portal sigue mostrando los datos que ya tenía, pero no recibe los nuevos."
    echo "    Revise: docker compose logs $PUBLICA"
fi
echo "================================================="
echo
echo "El sistema queda en MODO DEGRADADO hasta una ventana de mantenimiento:"
echo "  - El batch está detenido (no hay réplica de analítica)."
echo "  - La primaria vieja está apagada. NO la encienda: use siempre"
echo "        docker compose up -d --no-deps <servicio>"
echo "  - Pruebe ahora registrar un hallazgo en $URL/swagger-ui.html"
