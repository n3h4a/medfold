#!/usr/bin/env bash
# Renders every diagram in src/ to an SVG next to this script. Needs Node.js (and Chrome, which
# mermaid-cli downloads on first use; set PUPPETEER_CONFIG to point at another browser).
#   assets/diagrams/render.sh
set -euo pipefail
cd "$(dirname "$0")"
args=(-c mermaid.json -b white)
[ -n "${PUPPETEER_CONFIG:-}" ] && args+=(-p "$PUPPETEER_CONFIG")
for source in src/*.mmd; do
  name=$(basename "$source" .mmd)
  npx -y -p @mermaid-js/mermaid-cli@11 mmdc "${args[@]}" -i "$source" -o "$name.svg" >/dev/null
  echo "rendered $name.svg"
done
