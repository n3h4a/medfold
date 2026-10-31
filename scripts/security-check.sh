#!/usr/bin/env bash
# Probes a RUNNING Medfold from the outside, the way an attacker would, and prints PASS/FAIL per
# check. It creates two throwaway accounts and deletes them at the end.
#   scripts/security-check.sh                          # full Docker stack on http://localhost:3000
#   BASE_URL=https://medfold.example.com scripts/security-check.sh
# Also runs the dependency audits that need no extra setup (npm audit; pip-audit if installed).
# Exit code 1 if any check fails.
set -euo pipefail
cd "$(dirname "$0")/.."

status=0
BASE_URL=${BASE_URL:-http://localhost:3000} python3 - <<'EOF' || status=$?
import http.cookies, json, os, sys, urllib.error, urllib.request, uuid

BASE = os.environ["BASE_URL"].rstrip("/")
API = BASE + "/api/v1"
results = []

def check(name, ok, detail=""):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  ({detail})" if detail and not ok else ""))

def call(method, url, body=None, token=None, headers=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, dict(r.headers), r.read(), r.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read(), e.headers.get_all("Set-Cookie") or []

def multipart(filename, content, mime):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"

def register(email, password):
    status, _, body, cookies = call("POST", API + "/auth/register", {"email": email, "password": password, "fullName": "Security Check"})
    assert status == 201, f"register returned {status}: {body[:200]!r}"
    return json.loads(body)["accessToken"], cookies

print(f"Checking {BASE}\n")

# 1. Browser hardening headers on the app itself.
status, h, _, _ = call("GET", BASE + "/")
h = {k.lower(): v for k, v in h.items()}
csp = h.get("content-security-policy", "")
check("Content-Security-Policy restricts scripts to the app's own origin", "default-src 'self'" in csp and "'unsafe-inline'" not in csp.split("script-src")[-1].split(";")[0], csp[:120])
check("X-Content-Type-Options: nosniff", h.get("x-content-type-options") == "nosniff")
check("Framing blocked (X-Frame-Options or frame-ancestors)", "x-frame-options" in h or "frame-ancestors" in csp)
check("Referrer-Policy set", "referrer-policy" in h)
if BASE.startswith("https://"):
    check("HSTS on HTTPS", "strict-transport-security" in h)

# 2. Authentication.
status, _, body, _ = call("GET", API + "/documents")
check("No token: 401 with a JSON error", status == 401 and body.startswith(b"{"), str(status))
status, _, _, _ = call("GET", API + "/documents", token="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.forged")
check("Forged token: 401", status == 401, str(status))

password = "Sec-check-" + uuid.uuid4().hex[:12]
email_a = f"security-a-{uuid.uuid4().hex[:8]}@example.com"
email_b = f"security-b-{uuid.uuid4().hex[:8]}@example.com"
token_a, cookies_a = register(email_a, password)
token_b, _ = register(email_b, password)

cookie = next((c for c in cookies_a if c.startswith("medfold_refresh=")), "")
attrs = cookie.lower()
check("Refresh cookie is HttpOnly and SameSite=Strict", "httponly" in attrs and "samesite=strict" in attrs, cookie[:80])
if BASE.startswith("https://"):
    check("Refresh cookie is Secure on HTTPS", "secure" in attrs)

# 3. Refresh-token rotation and reuse detection.
first = cookie.split(";")[0]
status, _, _, rotated = call("POST", API + "/auth/refresh", headers={"Cookie": first})
second = next((c for c in rotated if c.startswith("medfold_refresh=")), "").split(";")[0]
check("Refresh rotates the token", status == 200 and second and second != first, str(status))
status, _, _, _ = call("POST", API + "/auth/refresh", headers={"Cookie": first})
status_after, _, _, _ = call("POST", API + "/auth/refresh", headers={"Cookie": second})
check("Reusing an old refresh token is refused and revokes the newer one too", status == 401 and status_after == 401, f"{status}/{status_after}")

# 4. Uploads are checked by content, not by name.
raw, ctype = multipart("not-really.pdf", b"just some text pretending to be a PDF", "application/pdf")
status, _, _, _ = call("POST", API + "/documents", raw=raw, token=token_a, content_type=ctype)
check("A text file named .pdf is rejected (415)", status == 415, str(status))
raw, ctype = multipart("report.pdf", b"%PDF-1.4\n% security check " + uuid.uuid4().hex.encode() + b"\n%%EOF\n", "application/pdf")
status, _, body, _ = call("POST", API + "/documents", raw=raw, token=token_a, content_type=ctype)
doc_id = json.loads(body)["id"] if status == 201 else None
check("A real PDF is accepted", status == 201, str(status))

# 5. Isolation: user B must not learn that A's document exists.
if doc_id:
    for method, path in [("GET", f"/documents/{doc_id}"), ("GET", f"/documents/{doc_id}/file"),
                         ("GET", f"/documents/{doc_id}/results"), ("GET", f"/documents/{doc_id}/status"),
                         ("PATCH", f"/documents/{doc_id}"), ("POST", f"/documents/{doc_id}/reprocess"),
                         ("DELETE", f"/documents/{doc_id}")]:
        status, _, _, _ = call(method, API + path, body={} if method == "PATCH" else None, token=token_b)
        check(f"Another user's {method} {path.replace(doc_id, '{id}')}: 404", status == 404, str(status))
    status, _, body, _ = call("GET", API + "/documents", token=token_b)
    check("Another user's list doesn't include it", status == 200 and doc_id not in body.decode())
    status, h, _, _ = call("GET", API + f"/documents/{doc_id}/file", token=token_a)
    h = {k.lower(): v for k, v in h.items()}
    check("The owner's file download is marked no-store", "no-store" in h.get("cache-control", ""), h.get("cache-control", ""))

# 6. Password guessing is throttled.
codes = [call("POST", API + "/auth/login", {"email": email_a, "password": "wrong-password-1"})[0] for _ in range(6)]
status, h, _, _ = call("POST", API + "/auth/login", {"email": email_a, "password": password})
check("After 5 wrong passwords even the right one is refused (429 + Retry-After)",
      codes[:5] == [401] * 5 and status == 429 and any(k.lower() == "retry-after" for k in h), f"{codes} then {status}")

# 7. The AI service is not reachable through the public entry point.
status, h, body, _ = call("POST", BASE + "/v1/extract", {"pages": []})
check("AI service endpoints are not exposed through the web entry point", not (status == 200 and body.startswith(b"{")) and status != 401, str(status))

# Clean up the throwaway accounts.
for token in (token_a, token_b):
    call("DELETE", API + "/account", {"password": password}, token=token)

failed = results.count(False)
print(f"\n{len(results) - failed}/{len(results)} checks passed")
sys.exit(1 if failed else 0)
EOF

echo
echo "Dependency audits"
(cd frontend && npm audit --omit=dev --audit-level=low) && echo "npm audit (runtime dependencies): no known vulnerabilities"
if command -v pip-audit >/dev/null; then
  pip-audit -r ai-service/requirements.txt && echo "pip-audit: no known vulnerabilities"
else
  echo "pip-audit not installed (pip install pip-audit) - skipped"
fi
exit $status
