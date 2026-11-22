#!/usr/bin/env bash
# =====================================================================
# Pruebas en navegador de la consola científica y del portal (Playwright).
#
# Abren un Chrome de verdad, entran como cada rol y usan las pantallas como lo haría
# una persona: escribir, hacer clic, leer lo que aparece.
#
# No hay que instalar Node ni navegadores: se usa la imagen de Docker de Playwright
# (la primera vez la descarga, ~1,5 GB, y unos 2 min). Necesita BioHub corriendo:
#   docker compose up -d
#
# Uso:
#   ./scripts/pruebas-ui.sh                       # todas
#   ./scripts/pruebas-ui.sh pruebas/analista.spec.js
#
# Resultado: en la consola, y un informe con capturas de las fallas en
# pruebas-ui/resultados/informe/index.html
# =====================================================================
set -eu
cd "$(dirname "$0")/.."

NGINX="$(docker compose ps -q nginx)"
[ -n "$NGINX" ] || { echo "ERROR: BioHub no está corriendo (docker compose up -d)"; exit 2; }
RED="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$NGINX")"

# La versión de la imagen debe ser la misma de pruebas-ui/package.json
IMAGEN="mcr.microsoft.com/playwright:v1.48.2-noble"

# MSYS_NO_PATHCONV evita que Git Bash en Windows cambie las rutas de abajo.
# node_modules va en un volumen de Docker: no ensucia la carpeta del proyecto.
MSYS_NO_PATHCONV=1 docker run --rm --init --ipc=host --network "$RED" \
    -e BIOHUB_URL=http://nginx -e CI=1 \
    -v "$(pwd)/pruebas-ui:/pruebas" \
    -v biohub-pruebas-ui-modulos:/pruebas/node_modules \
    -w /pruebas \
    "$IMAGEN" \
    bash -c 'npm install --no-audit --no-fund --loglevel=error && npx playwright test "$@"' _ "$@"
