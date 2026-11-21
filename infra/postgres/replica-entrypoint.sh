#!/bin/bash
# Arranque de una RÉPLICA de solo lectura.
# La primera vez copia la primaria completa (pg_basebackup) y queda en modo "standby":
# recibe cada cambio de la primaria y solo permite consultas de lectura.
set -e

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "[replica] Esperando a la primaria ($PRIMARIA_HOST)..."
    until pg_isready -h "$PRIMARIA_HOST" -p 5432 -U replicador > /dev/null 2>&1; do
        sleep 2
    done

    echo "[replica] Copiando la primaria con el slot $SLOT..."
    mkdir -p "$PGDATA"
    chown -R postgres:postgres "$PGDATA"
    chmod 700 "$PGDATA"
    gosu postgres env PGPASSWORD="$CLAVE_REPLICADOR" \
        pg_basebackup -h "$PRIMARIA_HOST" -p 5432 -U replicador -D "$PGDATA" -X stream -R -S "$SLOT"

    # Conexión a la primaria con clave, para reconectarse sola si se reinicia.
    echo "primary_conninfo = 'host=$PRIMARIA_HOST port=5432 user=replicador password=$CLAVE_REPLICADOR application_name=$SLOT'" \
        >> "$PGDATA/postgresql.auto.conf"
    echo "primary_slot_name = '$SLOT'" >> "$PGDATA/postgresql.auto.conf"
    chown postgres:postgres "$PGDATA/postgresql.auto.conf"
    echo "[replica] Lista. Arrancando en modo solo lectura."
fi

exec docker-entrypoint.sh postgres
