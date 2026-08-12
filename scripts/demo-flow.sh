#!/usr/bin/env bash
# Demo the async job flow without a browser (useful in remote/container Cursor).
set -euo pipefail

BASE="${1:-http://localhost:8080}"

echo "==> UI check"
curl -s -o /dev/null -w "GET / -> HTTP %{http_code}\n" "$BASE/"

echo
echo "==> Create job"
CREATE_RESP="$(curl -s -X POST "$BASE/api/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo job","details":"Created from terminal demo"}')"
echo "$CREATE_RESP"
JOB_ID="$(echo "$CREATE_RESP" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"

echo
echo "==> Poll status for job: $JOB_ID"
for i in 1 2 3 4 5 6 7 8 9 10; do
  STATUS_JSON="$(curl -s "$BASE/api/jobs/$JOB_ID")"
  STATUS="$(echo "$STATUS_JSON" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')"
  echo "[$i] status=$STATUS"
  if [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" ]]; then
    echo "$STATUS_JSON"
    break
  fi
  sleep 2
done

echo
echo "==> All jobs"
curl -s "$BASE/api/jobs"
echo
echo
echo "Done. Frontend HTML is served at $BASE/ but browser port-forward may be unavailable in this Cursor layout."
echo "Use this script, or open http/jobs.http with REST Client."
