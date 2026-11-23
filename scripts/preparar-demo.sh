#!/usr/bin/env bash
# =====================================================================
# Deja BioHub LIMPIO para la sustentación, solo con los datos de demostración.
#
# Las pruebas (las de navegador, las de carga, la siembra del millón) dejan muchas
# especies, grupos y equipos de prueba ("Ave ui …", "UI-…"). Eso es lo primero que
# vería el profesor. Este script:
#   1. crea .env si no existe (claves aleatorias). Va PRIMERO: sin .env, docker
#      compose ni siquiera puede leer su configuración, tampoco para apagar;
#   2. borra TODO (docker compose down -v): base, réplicas y Redis;
#   3. levanta todo y espera a que esté sano;
#   4. carga la historia de demostración (scripts/sembrar-demo.sh).
#
# Uso:   ./scripts/preparar-demo.sh        (pide escribir SI)
# =====================================================================
set -eu
cd "$(dirname "$0")/.."

echo "Esto BORRA todos los datos de BioHub y deja solo los de demostración."
printf "Escriba SI para seguir: "
read -r RESPUESTA
[ "$RESPUESTA" = "SI" ] || { echo "Cancelado."; exit 1; }

./scripts/preparar-env.sh
docker compose down -v
docker compose up -d --build --wait     # --wait: espera a que los healthchecks digan "sano"
./scripts/sembrar-demo.sh
./scripts/estado-salud.sh || true
echo
echo "Listo para la sustentación:"
echo "  Portal público:    http://localhost:8080"
echo "  Consola científica: http://localhost:8080/consola/"
