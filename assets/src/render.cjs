// Renders the README images from the HTML sources in this folder, with real numbers:
//   assets/SYSTEM_OVERVIEW.png            from system-overview.html
//   assets/charts/by-the-numbers.png       from by-the-numbers.html + assets/charts/stats.json + k6 results
//   assets/diagrams/request-flow.gif       from request-flow.html (needs ffmpeg)
// Run from the repository root: node assets/src/render.cjs   (needs Playwright with Chromium)
const { chromium } = require('playwright')
const { execFileSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const root = path.resolve(__dirname, '..', '..')
const src = (name) => 'file://' + path.join(__dirname, name)

function numbers() {
  const stats = JSON.parse(fs.readFileSync(path.join(root, 'assets/charts/stats.json'), 'utf8'))
  const k6 = JSON.parse(fs.readFileSync(path.join(root, 'tests/performance/results.json'), 'utf8')).metrics
  const reads = ['timeline', 'results', 'trend', 'search'].map((e) => k6[`http_req_duration{endpoint:${e}}`]['p(95)'])
  const lines = Object.values(stats.tests).map((s) => s.lines)
  const ciJobs = (fs.readFileSync(path.join(root, '.github/workflows/ci.yml'), 'utf8').split(/^jobs:\s*$/m)[1] ?? '')
    .split('\n').filter((l) => /^ {2}[a-z][\w-]*:\s*$/.test(l)).length
  return {
    tests: stats.testsTotal, backend: stats.tests.backend.tests, frontend: stats.tests.frontend.tests, ai: stats.tests.aiService.tests,
    minLines: Math.floor(Math.min(...lines)), maxLines: Math.floor(Math.max(...lines)),
    endpoints: stats.counts.backendEndpoints + stats.counts.aiEndpoints,
    backendEndpoints: stats.counts.backendEndpoints, aiEndpoints: stats.counts.aiEndpoints,
    tables: stats.counts.databaseTables, migrations: stats.counts.migrations, adrs: stats.counts.adrs,
    p95: '<' + Math.ceil(Math.max(...reads)), ciJobs, measuredOn: stats.measuredOn,
  }
}

;(async () => {
  const browser = await chromium.launch()

  const overview = await browser.newPage({ viewport: { width: 1600, height: 200 }, deviceScaleFactor: 1.5 })
  await overview.goto(src('system-overview.html'))
  await overview.screenshot({ path: path.join(root, 'assets/SYSTEM_OVERVIEW.png'), fullPage: true })

  const card = await browser.newPage({ viewport: { width: 1280, height: 400 }, deviceScaleFactor: 1.5 })
  await card.goto(src('by-the-numbers.html'))
  const n = numbers()
  await card.evaluate((s) => window.fill(s), n)
  await card.screenshot({ path: path.join(root, 'assets/charts/by-the-numbers.png'), fullPage: true })
  console.log('numbers:', JSON.stringify(n))

  const fps = 15
  const flow = await browser.newPage({ viewport: { width: 1200, height: 600 } })
  await flow.goto(src('request-flow.html'))
  const duration = await flow.evaluate(() => window.duration)
  const frames = fs.mkdtempSync(path.join(os.tmpdir(), 'medfold-flow-'))
  const count = Math.round(duration * fps)
  for (let i = 0; i < count; i++) {
    await flow.evaluate((t) => window.draw(t), i / fps)
    await flow.screenshot({ path: path.join(frames, `f${String(i).padStart(4, '0')}.png`) })
  }
  await browser.close()

  const gif = path.join(root, 'assets/diagrams/request-flow.gif')
  const filter = 'scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=none'
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(frames, 'f%04d.png'),
    '-filter_complex', filter, '-loop', '0', gif])
  fs.rmSync(frames, { recursive: true })
  console.log(`wrote SYSTEM_OVERVIEW.png, by-the-numbers.png and request-flow.gif (${count} frames)`)
})()
