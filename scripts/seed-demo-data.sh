#!/usr/bin/env bash
# Creates a demo account and uploads the synthetic reports in tests/fixtures, then waits until
# they are processed. Safe to run again: it logs in if the account exists, skips duplicates and
# re-queues documents that failed.
#   scripts/seed-demo-data.sh                      # local development (backend on :8080)
#   API_URL=http://localhost:3000/api/v1 scripts/seed-demo-data.sh   # full Docker setup
# Add WITH_PHOTO=1 to also upload a photographed prescription (needs Tesseract in the AI service).
set -euo pipefail
cd "$(dirname "$0")/.."

API_URL=${API_URL:-http://localhost:8080/api/v1}
EMAIL=${DEMO_EMAIL:-demo@medfold.local}
PASSWORD=${DEMO_PASSWORD:-medfold-demo-2026}
json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

body="{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Demo Patient\"}"
response=$(curl -s -X POST "$API_URL/auth/register" -H 'Content-Type: application/json' -d "$body")
if ! token=$(echo "$response" | json "['accessToken']" 2>/dev/null); then
  response=$(curl -s -X POST "$API_URL/auth/login" -H 'Content-Type: application/json' -d "$body")
  token=$(echo "$response" | json "['accessToken']") || { echo "Could not log in: $response" >&2; exit 1; }
  echo "Logged in as $EMAIL"
else
  echo "Created $EMAIL"
fi

files=(tests/fixtures/reports/*.pdf)
[ "${WITH_PHOTO:-}" = "1" ] && files+=(tests/fixtures/images/*.png)
for file in "${files[@]}"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/documents" \
    -H "Authorization: Bearer $token" -F "file=@$file")
  case $code in
    201) echo "Uploaded  $(basename "$file")" ;;
    409) echo "Skipped   $(basename "$file") (already uploaded)" ;;
    *)   echo "Failed    $(basename "$file") (HTTP $code)" >&2 ;;
  esac
done

# Documents that failed earlier (e.g. the AI service was down) are queued again.
failed=$(curl -s "$API_URL/documents?size=100" -H "Authorization: Bearer $token" | python3 -c \
  "import json,sys; print(' '.join(d['id'] for d in json.load(sys.stdin)['items'] if d['status'] == 'FAILED'))")
for id in $failed; do
  curl -s -o /dev/null -X POST "$API_URL/documents/$id/reprocess" -H "Authorization: Bearer $token"
  echo "Requeued  $id (failed earlier)"
done

echo "Waiting for processing..."
for _ in $(seq 1 90); do
  pending=$(curl -s "$API_URL/documents?size=100" -H "Authorization: Bearer $token" | python3 -c \
    "import json,sys; print(sum(d['status'] in ('UPLOADED','PROCESSING') for d in json.load(sys.stdin)['items']))")
  [ "$pending" = "0" ] && break
  sleep 2
done
# Prints every document and exits non-zero if any isn't READY (used by CI).
curl -s "$API_URL/documents?size=100" -H "Authorization: Bearer $token" | python3 -c "
import json, sys
items = json.load(sys.stdin)['items']
for d in items:
    reason = f\" ({d['failureReason']})\" if d['failureReason'] else ''
    print(f\"  {d['status']:<10} {d['docDate'] or '-':<10}  {d['title'] or d['originalFilename']}{reason}\")
sys.exit(0 if all(d['status'] == 'READY' for d in items) else 1)
" || { echo "Some documents were not processed." >&2; exit 1; }
echo
echo "Log in with $EMAIL / $PASSWORD"
