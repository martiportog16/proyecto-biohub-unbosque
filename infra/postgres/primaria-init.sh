#!/bin/bash
# Se ejecuta UNA vez, cuando la base primaria se crea por primera vez.
# Prepara la replicación hacia las dos réplicas (T-04 y T-23).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE replicador WITH REPLICATION LOGIN PASSWORD '${CLAVE_REPLICADOR}';
-- Un "slot" por réplica: la primaria guarda los cambios hasta que cada réplica los recibe.
SELECT pg_create_physical_replication_slot('replica_publica');
SELECT pg_create_physical_replication_slot('replica_analitica');
SQL

# Permite que el usuario replicador se conecte desde la red interna de Docker.
echo "host replication replicador all scram-sha-256" >> "$PGDATA/pg_hba.conf"
