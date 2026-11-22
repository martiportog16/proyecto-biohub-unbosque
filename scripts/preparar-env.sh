#!/usr/bin/env bash
# =====================================================================
# Crea el archivo .env con claves ALEATORIAS (T-17, T-14).
#
# Antes, si no había .env, BioHub usaba un secreto de tokens conocido por todos
# (estaba en el repositorio): cualquiera podía fabricarse un token de alto rango.
# Lo encontró la auditoría independiente. Ahora docker compose no arranca sin
# JWT_SECRETO, y este script lo genera.
#
# Uso (una sola vez, antes de docker compose up):   ./scripts/preparar-env.sh
# Si .env ya existe, no lo toca: solo revisa que tenga un secreto de verdad.
# .env NO se sube a GitHub (está en .gitignore).
# =====================================================================
set -eu
cd "$(dirname "$0")/.."

aleatorio() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c "${1:-40}"; }

if [ -f .env ]; then
  secreto="$(grep -s '^JWT_SECRETO=' .env | tail -n 1 | cut -d= -f2- || true)"
  if [ "${#secreto}" -lt 32 ] || echo "$secreto" | grep -q 'cambiar'; then
    echo "ERROR: .env existe, pero JWT_SECRETO falta, es corto o es el de ejemplo."
    echo "Bórrelo (rm .env) y vuelva a correr este script. Ojo: cambiar las claves de la base exige docker compose down -v."
    exit 1
  fi
  echo ".env ya existe y tiene un secreto válido. No se cambió nada."
  exit 0
fi

cat > .env <<FIN
# Generado por scripts/preparar-env.sh el $(date '+%Y-%m-%d %H:%M'). NO subir a GitHub.
DB_DUENO=biohub
DB_CLAVE_DUENO=$(aleatorio 24)
DB_CLAVE_APP_CIENTIFICO=$(aleatorio 24)
DB_CLAVE_APP_PUBLICO=$(aleatorio 24)
DB_CLAVE_APP_BATCH=$(aleatorio 24)
DB_CLAVE_REPLICADOR=$(aleatorio 24)
JWT_SECRETO=$(aleatorio 48)
PUERTO_BIOHUB=8080
FIN
echo "Listo: .env creado con claves aleatorias. Ahora: docker compose up -d --build"
