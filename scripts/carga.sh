#!/usr/bin/env bash
# =====================================================================
# Corre una prueba de carga de k6 (paquete 13). No hay que instalar k6:
# se usa su imagen de Docker, conectada a la red interna de BioHub.
#
# Mientras corre, mide el uso de CPU de la base primaria (P-20: debe quedar < 80 %).
# Guarda el resumen en pruebas-carga/resultados/.
#
# Uso:   ./scripts/carga.sh pico-publico            # PR-02 + PR-03, 5 minutos
#        DURACION=1m ./scripts/carga.sh pico-publico   # versión corta para ensayar
#        ./scripts/carga.sh registro
# =====================================================================
set -u
cd "$(dirname "$0")/.."

PRUEBA="${1:?Indique la prueba: pico-publico o registro}"
[ -f "pruebas-carga/$PRUEBA.js" ] || { echo "No existe pruebas-carga/$PRUEBA.js"; exit 2; }
ETIQUETA="${ETIQUETA:-$PRUEBA}"
valor_env() { [ -f .env ] && grep -s "^$1=" .env | tail -n 1 | cut -d= -f2-; }
PRIMARIA="$(valor_env DB_HOST_PRIMARIA)"; PRIMARIA="${PRIMARIA:-postgres-primaria}"

NGINX="$(docker compose ps -q nginx)"
[ -n "$NGINX" ] || { echo "ERROR: BioHub no está corriendo (docker compose up -d)"; exit 2; }
RED="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$NGINX")"
mkdir -p pruebas-carga/resultados
# k6 corre con otro usuario dentro de su contenedor: debe poder escribir aquí
chmod 777 pruebas-carga/resultados 2>/dev/null
SALIDA="pruebas-carga/resultados/$ETIQUETA-$(date +%Y%m%d-%H%M%S)"

# CPU de la primaria cada 5 s, en segundo plano
CONTENEDOR_PRIMARIA="$(docker compose ps -q "$PRIMARIA")"
( while true; do
    docker stats --no-stream --format '{{.CPUPerc}}' "$CONTENEDOR_PRIMARIA" 2>/dev/null | tr -d '%' >> "$SALIDA-cpu.txt"
    sleep 5
  done ) &
MEDIDOR=$!
trap 'kill $MEDIDOR 2>/dev/null' EXIT

echo "Corriendo $PRUEBA (resultados en $SALIDA-*)..."
# MSYS_NO_PATHCONV evita que Git Bash en Windows cambie las rutas de abajo
MSYS_NO_PATHCONV=1 docker run --rm -i --network "$RED" \
    -e URL=http://nginx -e DURACION="${DURACION:-}" -e TASA_PORTAL="${TASA_PORTAL:-}" \
    -e TASA_REGISTRO="${TASA_REGISTRO:-}" -e TASA="${TASA:-}" \
    -v "$(pwd)/pruebas-carga:/pruebas" \
    grafana/k6:0.54.0 run --quiet --summary-export "/pruebas/resultados/$(basename "$SALIDA")-resumen.json" \
    "/pruebas/$PRUEBA.js" | tee "$SALIDA-salida.txt"
CODIGO=${PIPESTATUS[0]}

kill $MEDIDOR 2>/dev/null
CPU_MAX="$(sort -n "$SALIDA-cpu.txt" 2>/dev/null | tail -n 1)"
NUCLEOS="$(docker info --format '{{.NCPU}}' 2>/dev/null || echo 1)"
echo
echo "CPU máxima de la base primaria: ${CPU_MAX:-?} % (docker cuenta 100 % por núcleo; esta máquina tiene $NUCLEOS núcleos)"
echo "P-20 pide < 80 % del total, es decir < $(( 80 * NUCLEOS )) % en esta escala."
if [ "$CODIGO" -eq 0 ]; then echo "$PRUEBA: TODOS LOS UMBRALES SE CUMPLEN"; else echo "$PRUEBA: ALGÚN UMBRAL NO SE CUMPLE (ver arriba)"; fi
exit "$CODIGO"
