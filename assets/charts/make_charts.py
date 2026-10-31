"""
Draws the charts in docs/05-testing from real measurements:

- latency.svg  from tests/performance/results.json (written by the k6 load test)
- coverage.svg from coverage.json next to this script (numbers from the three coverage reports)

    python assets/charts/make_charts.py
"""

import json
from html import escape
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
LIGHT, DARK = "#8fd3c9", "#0f766e"   # one hue, light → dark: two levels of the same measure
INK, MUTED, GRID = "#0f172a", "#475569", "#e2e8f0"
FONT = "font-family=\"Inter, 'Segoe UI', Helvetica, Arial, sans-serif\""


def bar_chart(title, subtitle, rows, series, unit, max_value, path, tick_step, fmt):
    """Horizontal grouped bars. rows: [(label, [value per series])]."""
    left, right, top, bar_h, gap, group_gap = 150, 70, 92, 14, 3, 18
    width = 760
    plot_w = width - left - right
    group_h = len(series) * bar_h + (len(series) - 1) * gap
    height = top + len(rows) * (group_h + group_gap) + 34
    x = lambda v: left + plot_w * v / max_value
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{escape(title)}">',
           f'<rect width="{width}" height="{height}" rx="12" fill="#ffffff"/>',
           f'<text x="24" y="34" {FONT} font-size="17" font-weight="600" fill="{INK}">{escape(title)}</text>',
           f'<text x="24" y="56" {FONT} font-size="12.5" fill="{MUTED}">{escape(subtitle)}</text>']
    lx = 24
    for (name, color) in series:  # legend
        out.append(f'<rect x="{lx}" y="68" width="12" height="12" rx="3" fill="{color}"/>')
        out.append(f'<text x="{lx + 18}" y="78.5" {FONT} font-size="12" fill="{MUTED}">{escape(name)}</text>')
        lx += 18 + 7.2 * len(name) + 22
    bottom = height - 30
    t = 0
    while t <= max_value + 1e-9:  # recessive grid
        out.append(f'<line x1="{x(t):.1f}" y1="{top - 6}" x2="{x(t):.1f}" y2="{bottom}" stroke="{GRID}" stroke-width="1"/>')
        out.append(f'<text x="{x(t):.1f}" y="{bottom + 16}" {FONT} font-size="11" fill="{MUTED}" text-anchor="middle">{fmt(t)}{unit}</text>')
        t += tick_step
    y = top
    for label, values in rows:
        out.append(f'<text x="{left - 12}" y="{y + group_h / 2 + 4:.1f}" {FONT} font-size="12.5" fill="{INK}" text-anchor="end">{escape(label)}</text>')
        for i, ((name, color), v) in enumerate(zip(series, values)):
            by = y + i * (bar_h + gap)
            w = max(x(v) - left, 2)
            out.append(f'<rect x="{left}" y="{by}" width="{w:.1f}" height="{bar_h}" rx="3" fill="{color}"><title>{escape(label)}, {escape(name)}: {fmt(v)}{unit}</title></rect>')
            out.append(f'<text x="{left + w + 6:.1f}" y="{by + bar_h - 3}" {FONT} font-size="11" fill="{MUTED}">{fmt(v)}{unit}</text>')
        y += group_h + group_gap
    out.append("</svg>")
    path.write_text("\n".join(out) + "\n")


def latency():
    m = json.loads((ROOT / "tests/performance/results.json").read_text())["metrics"]
    endpoints = [("Trend", "trend"), ("Timeline", "timeline"), ("Results", "results"), ("Search", "search"), ("Upload", "upload")]
    rows = [(label, [m[f"http_req_duration{{endpoint:{key}}}"]["med"], m[f"http_req_duration{{endpoint:{key}}}"]["p(95)"]]) for label, key in endpoints]
    total = m["http_reqs"]["count"]
    failed = m["http_req_failed"]["value"] * 100
    bar_chart("API latency under load", f"20 concurrent users for 60 s, {total:,} requests, {failed:.2f}% failed (k6, local stack)",
              rows, [("Median", LIGHT), ("95th percentile", DARK)], " ms", 20, HERE / "latency.svg", 5, lambda v: f"{v:.1f}" if v % 1 else f"{v:.0f}")


def coverage():
    c = json.loads((HERE / "coverage.json").read_text())
    rows = [(s["service"], [s["lines"], s["branches"]]) for s in c["services"]]
    bar_chart("Test coverage", f"Measured {c['measured']} from the JaCoCo, Vitest (V8) and pytest-cov reports",
              rows, [("Lines", DARK), ("Branches", LIGHT)], "%", 100, HERE / "coverage.svg", 25, lambda v: f"{v:.1f}" if v % 1 else f"{v:.0f}")


if __name__ == "__main__":
    latency()
    coverage()
    print("wrote latency.svg and coverage.svg")
