#!/bin/sh
set -eu
# Render supplies a PostgreSQL URL; JDBC requires its own scheme and separate credentials.
# Never print the URL, credentials, or shell tracing here.
if [ -n "${DATABASE_URL:-}" ] && [ -z "${DB_URL:-}" ]; then
    case "$DATABASE_URL" in
        postgresql://*) database_address=${DATABASE_URL#postgresql://} ;;
        postgres://*) database_address=${DATABASE_URL#postgres://} ;;
        *) echo 'DATABASE_URL must be a PostgreSQL URL.' >&2; exit 1 ;;
    esac
    database_address=${database_address##*@}
    export DB_URL="jdbc:postgresql://$database_address"
fi
exec java -XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
