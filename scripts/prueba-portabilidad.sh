#!/usr/bin/env bash
# =====================================================================
# PR-12 (RNF-08): si cambia la dirección interna de los servidores, el
# contrato externo /v1 no cambia y la suite externa sigue pasando.
#
# Qué hace:
#   1. Corre la suite externa con la configuración normal.
#   2. CAMBIA la dirección interna del grupo público en nginx.conf:
#      del nombre "app-publico" a las direcciones IP de cada instancia,
#      como si los servidores se hubieran movido de lugar.
#   3. Recarga Nginx y vuelve a correr la MISMA suite, sin cambiarle nada.
#   4. Deja nginx.conf como estaba (aunque algo falle).
#
# Uso (con el sistema arriba):   ./scripts/prueba-portabilidad.sh
# =====================================================================
set -u
cd "$(dirname "$0")/.."

CONF=infra/nginx/nginx.conf
RESPALDO="$(mktemp)"
cp "$CONF" "$RESPALDO"
restaurar() {
    cp "$RESPALDO" "$CONF"; rm -f "$RESPALDO"
    docker compose exec -T nginx nginx -s reload > /dev/null 2>&1
}
trap restaurar EXIT

# Espera a que el límite por minuto de la ONG se reinicie, para que PR-11 sea comparable
esperar_minuto_nuevo() {
    local s; s=$(( 60 - $(date +%s) % 60 + 1 ))
    echo "(Esperando ${s}s a que empiece un minuto nuevo para el límite por consumidor...)"
    sleep "$s"
}

echo "=============== 1. Configuración normal ==============="
grep -A2 'upstream grupo_publico' "$CONF" | sed 's/^/    /'
esperar_minuto_nuevo
./scripts/suite-externa.sh; ANTES=$?

echo
echo "=============== 2. Los servidores 'se mueven' ==============="
IPS=""
for c in $(docker compose ps -q app-publico); do
    IPS="$IPS $(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$c")"
done
[ -n "${IPS// /}" ] || { echo "ERROR: no se encontraron instancias públicas"; exit 2; }
NUEVO=""
for ip in $IPS; do NUEVO="$NUEVO        server $ip:8080 max_fails=1 fail_timeout=15s;\n"; done
# Reemplaza la línea "server app-publico:8080 ..." por una línea por cada IP
awk -v nuevo="$NUEVO" '
    /upstream grupo_publico/ { dentro=1 }
    dentro && /server app-publico:8080/ { printf "%s", nuevo; dentro=0; next }
    { print }' "$RESPALDO" > "$CONF"
echo "Nuevo bloque en nginx.conf:"
grep -A3 'upstream grupo_publico' "$CONF" | sed 's/^/    /'
docker compose exec -T nginx nginx -t > /dev/null 2>&1 || { echo "ERROR: la configuración nueva no es válida"; exit 2; }
docker compose exec -T nginx nginx -s reload > /dev/null 2>&1

echo
echo "=============== 3. La MISMA suite, sin cambios ==============="
esperar_minuto_nuevo
./scripts/suite-externa.sh; DESPUES=$?

echo
echo "================ Resultado PR-12 ================"
echo "Suite con la dirección original:  $([ $ANTES -eq 0 ] && echo PASA || echo FALLA)"
echo "Suite con la dirección nueva:     $([ $DESPUES -eq 0 ] && echo PASA || echo FALLA)"
echo "================================================="
echo "(nginx.conf queda como estaba)"
if [ $ANTES -eq 0 ] && [ $DESPUES -eq 0 ]; then
    echo "PR-12 CUMPLE: cambió la dirección interna y el contrato /v1 siguió igual."
    exit 0
fi
echo "PR-12 NO CUMPLE."
exit 1
