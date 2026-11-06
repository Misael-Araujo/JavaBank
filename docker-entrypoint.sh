#!/bin/sh
set -eu

if [ -z "${JWT_SECRET:-}" ]; then
    JWT_SECRET="$(od -An -N48 -tx1 /dev/urandom | tr -d ' \n')"
    export JWT_SECRET
fi

exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar
