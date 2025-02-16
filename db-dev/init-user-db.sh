#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER partyboi_test;
	CREATE DATABASE ${POSTGRES_DB}_test;
	GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB}_test TO partyboi_test;
EOSQL
