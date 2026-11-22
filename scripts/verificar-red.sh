#!/usr/bin/env bash
# =====================================================================
# PR-10 (RNF-07): desde fuera de la red de Docker solo responde Nginx.
#
# Revisa tres cosas:
#   1. Qué contenedores publican puertos hacia afuera: solo debe ser Nginx.
#   2. Que BioHub no responda en los puertos típicos de la base de datos, Redis
#      y la app desde esta máquina.
#   3. Que por Nginx no se llegue a las rutas internas (/actuator).
#
# Uso (con el sistema arriba):   ./scripts/verificar-red.sh
# Funciona en Linux, macOS, WSL y Git Bash. Necesita docker y curl.
# =====================================================================
set -u
cd "$(dirname "$0")/.."

valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2-; }
PUERTO="${PUERTO_BIOHUB:-$(valor_env PUERTO_BIOHUB)}"; PUERTO="${PUERTO:-8080}"
FALLAS=0

bien() { echo "  [OK]    $*"; }
mal()  { echo "  [FALLA] $*"; FALLAS=$((FALLAS + 1)); }

echo "1. Puertos publicados por cada contenedor"
for c in $(docker compose ps -q); do
    NOMBRE="$(docker inspect -f '{{.Name}}' "$c" | sed 's#^/##')"
    PUBLICADOS="$(docker port "$c" 2>/dev/null | tr '\n' ' ')"
    case "$NOMBRE" in
        *nginx*)
            if [ -n "$PUBLICADOS" ]; then bien "$NOMBRE publica: $PUBLICADOS"; else mal "$NOMBRE no publica ningún puerto"; fi ;;
        *)
            if [ -z "$PUBLICADOS" ]; then bien "$NOMBRE no publica puertos"; else mal "$NOMBRE publica: $PUBLICADOS"; fi ;;
    esac
done

echo
echo "2. Puertos internos probados desde esta máquina (BioHub no debe responder en ellos)"
abierto() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
# Todos los puertos de esta máquina que algún contenedor de BioHub publica (del paso 1)
PUBLICADOS_BIOHUB=" $(for c in $(docker compose ps -q); do docker port "$c" 2>/dev/null | sed 's/.*://'; done | tr '\n' ' ')"
for par in "5432:PostgreSQL" "6379:Redis" "8081:aplicación" "9090:métricas"; do
    P="${par%%:*}"; QUE="${par##*:}"
    if [ "$P" = "$PUERTO" ]; then continue; fi
    if ! abierto "$P"; then
        bien "el puerto $P ($QUE) no responde"
    elif echo "$PUBLICADOS_BIOHUB" | grep -q " $P "; then
        mal "el puerto $P ($QUE) responde y lo publica un contenedor de BioHub"
    else
        echo "  [AVISO] el puerto $P ($QUE) responde, pero ningún contenedor de BioHub lo publica:"
        echo "          es otro programa instalado en esta máquina, no BioHub."
    fi
done
if abierto "$PUERTO"; then bien "el puerto $PUERTO (Nginx) responde"; else mal "Nginx no responde en el puerto $PUERTO"; fi

echo
echo "3. Rutas por Nginx"
codigo() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:$PUERTO$1"; }
revisar() {  # ruta, código esperado, explicación
    C="$(codigo "$1")"
    if [ "$C" = "$2" ]; then bien "$1 -> $C ($3)"; else mal "$1 -> $C, se esperaba $2 ($3)"; fi
}
revisar /api/publico/estado      200 "portal público"
revisar /api/cientifico/estado   200 "grupo científico"
revisar /actuator/health         404 "salud interna, no expuesta"
revisar /actuator/metrics        404 "métricas internas, no expuestas"
revisar /actuator/env            404 "configuración interna, no expuesta"

echo
if [ "$FALLAS" -eq 0 ]; then
    echo "PR-10 CUMPLE: el único punto de entrada es Nginx."
    exit 0
fi
echo "PR-10 NO CUMPLE: $FALLAS revisiones fallaron."
exit 1
