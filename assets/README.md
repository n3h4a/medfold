# Assets

| Folder | Contents | Regenerate |
|---|---|---|
| `logo/` | Medfold mark and logo (SVG); the mark is also the app's favicon | Hand-made |
| `branding/` | `banner.png`: README hero, 1280×640, also usable as the GitHub social preview | From the logo and a screenshot |
| `diagrams/` | Architecture, component, sequence, state, class, ER, pipeline, flow and timeline diagrams (SVG); Mermaid sources in `src/` | `assets/diagrams/render.sh` |
| `charts/` | Coverage and latency charts from real measurements; `stats.json` holds every measured number | `scripts/verify-claims.sh`, then `python assets/charts/make_charts.py` |
| `SYSTEM_OVERVIEW.png`, `charts/by-the-numbers.png`, `diagrams/request-flow.gif` | The README's overview image, numbers card and animated request flow, drawn from the HTML in `src/` (numbers from `stats.json` and the k6 results) | `node assets/src/render.cjs` (Playwright + ffmpeg) |

Demo GIF, video and screenshots are in [`demo/`](../demo).
