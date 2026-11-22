#!/usr/bin/env bash
# =====================================================================
# T-11 (RNF-04): monitoreo de salud de BioHub.
#
# Muestra, en una sola pantalla:
#   1. El estado de cada contenedor según su healthcheck (healthy / unhealthy / starting).
#   2. Qué tan atrasada va cada réplica respecto de la primaria (consistencia eventual).
#   3. Si el portal, el registro y la API externa responden por Nginx.
#
# Uso:
#   ./scripts/estado-salud.sh               # una revisión
#   ./scripts/estado-salud.sh --vigilar     # repite cada 10 s (Ctrl+C para salir)
#   ./scripts/estado-salud.sh --reparar     # además reinicia las instancias "unhealthy"
#                                           # y recarga Nginx (retiro y regreso al balanceo)
#
# Funciona en Linux, macOS, WSL y Git Bash. Necesita docker y curl.
# Sale con código 0 si todo está sano y 1 si algo no lo está (sirve para un CI o un cron).
# =====================================================================
set -u
cd "$(dirname "$0")/.."

valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }
DUENO="${DB_DUENO:-$(valor_env DB_DUENO)}"; DUENO="${DUENO:-biohub}"
PUERTO="${PUERTO_BIOHUB:-$(valor_env PUERTO_BIOHUB)}"; PUERTO="${PUERTO:-8080}"
URL="${BIOHUB_URL:-http://localhost:$PUERTO}"
PRIMARIA="${DB_HOST_PRIMARIA:-$(valor_env DB_HOST_PRIMARIA)}"; PRIMARIA="${PRIMARIA:-postgres-primaria}"
ATRASO_MAXIMO_MS=5000

MODO="${1:-}"

revisar() {
  local problemas=0
  echo "=== BioHub · estado de salud · $(date '+%Y-%m-%d %H:%M:%S') ==="
  echo
  echo "1. Contenedores"
  printf '   %-36s %-10s %s\n' "CONTENEDOR" "ESTADO" "SALUD"
  while IFS='|' read -r nombre estado salud; do
    [ -z "$nombre" ] && continue
    [ -z "$salud" ] && salud="(sin healthcheck)"
    marca="ok"
    if [ "$estado" != "running" ] || [ "$salud" = "unhealthy" ]; then marca="PROBLEMA"; problemas=$((problemas + 1)); fi
    printf '   %-36s %-10s %-18s %s\n' "$nombre" "$estado" "$salud" "$marca"
    if [ "$MODO" = "--reparar" ] && [ "$salud" = "unhealthy" ]; then
      echo "      -> reiniciando $nombre"
      docker restart "$nombre" >/dev/null && REINICIADOS=1
    fi
  done < <(docker compose ps -a --format '{{.Name}}|{{.State}}|{{.Health}}' 2>/dev/null)

  echo
  echo "2. Réplicas (qué tan atrás van de la primaria, $PRIMARIA)"
  local filas
  filas="$(docker compose exec -T "$PRIMARIA" psql -U "$DUENO" -d biohub -Atc \
    "SELECT application_name, state, COALESCE((EXTRACT(EPOCH FROM replay_lag) * 1000)::int, 0)
       FROM pg_stat_replication ORDER BY 1" 2>/dev/null)"
  if [ -z "$filas" ]; then
    echo "   PROBLEMA: ninguna réplica está conectada a la primaria"
    problemas=$((problemas + 1))
  else
    while IFS='|' read -r replica estado atraso; do
      marca="ok"
      if [ "$estado" != "streaming" ] || [ "$atraso" -gt "$ATRASO_MAXIMO_MS" ]; then marca="PROBLEMA"; problemas=$((problemas + 1)); fi
      printf '   %-20s %-10s atraso %6s ms   %s\n' "$replica" "$estado" "$atraso" "$marca"
    done <<< "$filas"
  fi

  echo
  echo "3. Servicios por Nginx (lo que ve un usuario)"
  for ruta in "/" "/api/publico/especies" "/api/v1/estado" "/api/cientifico/especies"; do
    codigo="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$URL$ruta")"
    # /api/cientifico pide sesión: 401 significa que la instancia respondió
    esperado=200; [ "$ruta" = "/api/cientifico/especies" ] && esperado=401
    [ "$ruta" = "/api/v1/estado" ] && esperado=401
    marca="ok"; [ "$codigo" != "$esperado" ] && { marca="PROBLEMA"; problemas=$((problemas + 1)); }
    printf '   %-28s HTTP %s (se espera %s)   %s\n' "$ruta" "$codigo" "$esperado" "$marca"
  done

  echo
  if [ "$problemas" -eq 0 ]; then echo "RESULTADO: todo sano"; else echo "RESULTADO: $problemas problema(s)"; fi
  return "$problemas"
}

REINICIADOS=0
if [ "$MODO" = "--vigilar" ]; then
  while true; do clear 2>/dev/null; revisar; sleep 10; done
fi
revisar; resultado=$?
if [ "$REINICIADOS" = "1" ]; then
  echo
  echo "Se reinició al menos una instancia. Esperando 30 s y recargando Nginx para que la vuelva a usar..."
  sleep 30
  docker compose exec -T nginx nginx -s reload >/dev/null && echo "Nginx recargado."
fi
[ "$resultado" -eq 0 ]
