#!/usr/bin/env bash
# Fix Hikari stuck on H2 lock: kill old processes, clear lock, restart both apps.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Stopping old job-api / job-worker processes"
pkill -9 -f 'com.example.job.api.JobApiApplication' 2>/dev/null || true
pkill -9 -f 'com.example.job.worker.JobWorkerApplication' 2>/dev/null || true
pkill -9 -f 'MavenWrapperMain spring-boot:run' 2>/dev/null || true
sleep 1

echo "==> Resetting local H2 DB (avoids missing-column errors after schema changes)"
rm -f "$ROOT/data/jobsdb.mv.db" "$ROOT/data/jobsdb.lock.db" "$ROOT/data/jobsdb.trace.db"
mkdir -p "$ROOT/data"

echo "==> Starting job-api (terminal logs below). Start worker in another terminal:"
echo "    cd $ROOT/job-worker && ../mvnw spring-boot:run"
echo
cd "$ROOT/job-api"
../mvnw spring-boot:run
