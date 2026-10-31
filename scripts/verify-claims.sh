#!/usr/bin/env bash
# Re-measures every number the README quotes, from the code itself, and writes them to
# assets/charts/stats.json (which the "by the numbers" card is drawn from).
#   scripts/verify-claims.sh            # runs all three test suites with coverage (~4 minutes)
#   COUNT_ONLY=1 scripts/verify-claims.sh   # only the counts that need no test run
# Needs: JDK 21 + Docker (backend tests use Testcontainers), Node 20+, the AI service venv
# (scripts/start-dev.sh creates ai-service/.venv) and Tesseract for the 7 OCR tests.
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'find "$out" -delete' EXIT

if [ "${COUNT_ONLY:-}" != "1" ]; then
  echo "Backend: tests + JaCoCo"
  (cd backend && ./mvnw -q -B verify) > "$out/backend.log" 2>&1 || { tail -40 "$out/backend.log"; exit 1; }

  echo "Frontend: tests + coverage"
  (cd frontend && npx vitest run --coverage --coverage.reporter=json-summary) > "$out/frontend.log" 2>&1 \
    || { tail -40 "$out/frontend.log"; exit 1; }

  echo "AI service: tests + coverage"
  python=${AI_PYTHON:-ai-service/.venv/bin/python}
  (cd ai-service && "../$python" -m pytest -q --cov --cov-report="json:$out/ai-cov.json" --junitxml="$out/ai.xml") \
    > "$out/ai.log" 2>&1 || { tail -40 "$out/ai.log"; exit 1; }
fi

OUT="$out" COUNT_ONLY="${COUNT_ONLY:-}" python3 - <<'EOF'
import csv, glob, json, os, re, xml.etree.ElementTree as ET
from datetime import date

out, count_only = os.environ["OUT"], os.environ["COUNT_ONLY"] == "1"
stats = json.load(open("assets/charts/stats.json")) if os.path.exists("assets/charts/stats.json") else {}

def grep_count(pattern, files):
    return sum(len(re.findall(pattern, open(f, encoding="utf-8").read())) for f in files)

java = glob.glob("backend/src/main/java/**/*.java", recursive=True)
python = glob.glob("ai-service/app/**/*.py", recursive=True)
migrations = sorted(glob.glob("backend/src/main/resources/db/migration/V*.sql"))
stats["counts"] = {
    "backendEndpoints": grep_count(r"@(?:Get|Post|Put|Patch|Delete)Mapping", java),
    "aiEndpoints": grep_count(r"@(?:router|app)\.(?:get|post|put|patch|delete)\(", python),
    "databaseTables": grep_count(r"(?i)\bCREATE TABLE\b", migrations),
    "migrations": len(migrations),
    "adrs": len(glob.glob("docs/03-design/adr/ADR-*.md")),
}

if not count_only:
    tests = {}
    t = f = 0
    for p in glob.glob("backend/target/surefire-reports/TEST-*.xml"):
        r = ET.parse(p).getroot()
        t += int(r.get("tests")); f += int(r.get("failures")) + int(r.get("errors"))
    rows = list(csv.DictReader(open("backend/target/site/jacoco/jacoco.csv")))
    pct = lambda k: round(100 * sum(int(r[k + "_COVERED"]) for r in rows)
                          / sum(int(r[k + "_COVERED"]) + int(r[k + "_MISSED"]) for r in rows), 1)
    tests["backend"] = {"tests": t, "failed": f, "lines": pct("LINE"), "branches": pct("BRANCH")}

    log = open(f"{out}/frontend.log", encoding="utf-8").read()
    passed = int(re.search(r"Tests\s+(?:\x1b\[[0-9;]*m)*(\d+) passed", log).group(1))
    total = json.load(open("frontend/coverage/coverage-summary.json"))["total"]
    tests["frontend"] = {"tests": passed, "failed": 0, "lines": round(total["lines"]["pct"], 1), "branches": round(total["branches"]["pct"], 1)}

    r = ET.parse(f"{out}/ai.xml").getroot()
    suite = r if r.tag == "testsuite" else r[0]
    cov = json.load(open(f"{out}/ai-cov.json"))["totals"]
    tests["aiService"] = {
        "tests": int(suite.get("tests")), "skipped": int(suite.get("skipped")),
        "failed": int(suite.get("failures")) + int(suite.get("errors")),
        "lines": round(100 * cov["covered_lines"] / cov["num_statements"], 1),
        "branches": round(100 * cov["covered_branches"] / cov["num_branches"], 1),
    }
    stats["tests"] = tests
    stats["testsTotal"] = sum(s["tests"] for s in tests.values())
    stats["measuredOn"] = date.today().isoformat()

json.dump(stats, open("assets/charts/stats.json", "w"), indent=2)
open("assets/charts/stats.json", "a").write("\n")

c = stats["counts"]
print(f"\n{'Claim':<34}Measured")
print(f"{'REST endpoints (backend + AI)':<34}{c['backendEndpoints']} + {c['aiEndpoints']}")
print(f"{'Database tables / migrations':<34}{c['databaseTables']} / {c['migrations']}")
print(f"{'Architecture decision records':<34}{c['adrs']}")
for name, s in stats.get("tests", {}).items():
    skipped = f", {s['skipped']} skipped" if s.get("skipped") else ""
    print(f"{name + ' tests':<34}{s['tests']} ({s['failed']} failed{skipped}), {s['lines']}% lines, {s['branches']}% branches")
if "testsTotal" in stats:
    print(f"{'Total tests':<34}{stats['testsTotal']}  (measured {stats['measuredOn']})")
EOF
