// Records the demo video (about 80 seconds): demo/demo-video.mp4 (and demo/quick-demo.gif from it).
//
//   scripts/start-dev.sh                 # app on :5173, API on :8080
//   scripts/seed-demo-data.sh            # demo account + sample reports
//   xvfb-run -a node demo/src/record-demo.cjs
//
// Runs a real (headed) Chromium so the PDF viewer renders; xvfb-run provides a screen on servers.
// Needs Playwright and ffmpeg. Captions and the cursor are drawn into the page while recording.
const { chromium } = require('playwright')
const { execFileSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const APP = process.env.APP_URL ?? 'http://localhost:5173'
const API = process.env.API_URL ?? 'http://localhost:8080/api/v1'
const EMAIL = 'demo@medfold.local'
const PASSWORD = 'medfold-demo-2026'
const UPLOAD = path.resolve(__dirname, '../../tests/sample-pdfs/live-upload/thyroid-profile-2026-09-12.pdf')
const OUT = path.resolve(__dirname, '..')
const SIZE = { width: 1280, height: 800 }
const pause = (ms) => new Promise((r) => setTimeout(r, ms))

/** Removes the live-upload report if an earlier run left it, so it can be uploaded again on camera. */
async function resetLiveUpload() {
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  })
  if (!login.ok) throw new Error(`Log in failed (${login.status}): run scripts/seed-demo-data.sh first`)
  const { accessToken } = await login.json()
  const auth = { Authorization: `Bearer ${accessToken}` }
  const page = await (await fetch(`${API}/documents?size=100`, { headers: auth })).json()
  for (const d of page.items.filter((d) => d.originalFilename === path.basename(UPLOAD))) {
    await fetch(`${API}/documents/${d.id}`, { method: 'DELETE', headers: auth })
  }
}

/** Caption bar and a visible cursor, injected into every page load. */
function overlay() {
  if (window.top !== window) return // not inside the PDF viewer's frame
  const install = () => {
    if (document.getElementById('demo-caption')) return
    const style = document.createElement('style')
    style.textContent = `
      #demo-caption { position: fixed; left: 50%; bottom: 28px; transform: translateX(-50%); z-index: 99999; max-width: 1000px;
        display: flex; align-items: center; gap: 14px; padding: 14px 24px; border-radius: 16px; background: rgb(15 23 42 / 0.92);
        color: #fff; font: 600 21px/1.35 Inter, "Segoe UI", sans-serif; box-shadow: 0 12px 32px rgb(0 0 0 / 0.25); transition: opacity .3s; }
      #demo-caption b { flex: none; background: #2a8c84; border-radius: 9px; padding: 3px 10px; font-size: 16px; }
      #demo-caption:empty { opacity: 0; }
      #demo-cursor { position: fixed; z-index: 100000; width: 22px; height: 22px; pointer-events: none; left: -40px; top: -40px;
        transition: transform .12s; filter: drop-shadow(0 2px 3px rgb(0 0 0 / .35)); }
      #demo-cursor.down { transform: scale(.82); }`
    document.head.appendChild(style)
    const caption = document.createElement('div')
    caption.id = 'demo-caption'
    caption.innerHTML = sessionStorage.getItem('demo-caption') ?? ''
    const cursor = document.createElement('div')
    cursor.id = 'demo-cursor'
    cursor.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 2l15 11-7 1 4 8-3 1-4-8-5 5z" fill="#fff" stroke="#0f172a" stroke-width="1.5" stroke-linejoin="round"/></svg>'
    document.body.append(caption, cursor)
    const last = JSON.parse(sessionStorage.getItem('demo-cursor') ?? 'null')
    if (last) Object.assign(cursor.style, { left: last[0] + 'px', top: last[1] + 'px' })
    addEventListener('mousemove', (e) => {
      Object.assign(cursor.style, { left: e.clientX + 'px', top: e.clientY + 'px' })
      sessionStorage.setItem('demo-cursor', JSON.stringify([e.clientX, e.clientY]))
    })
    addEventListener('mousedown', () => cursor.classList.add('down'))
    addEventListener('mouseup', () => cursor.classList.remove('down'))
  }
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', install)
  else install()
}

;(async () => {
  await resetLiveUpload()
  const browser = await chromium.launch({ headless: false })
  const recordings = fs.mkdtempSync(path.join(os.tmpdir(), 'medfold-demo-'))
  const context = await browser.newContext({ viewport: SIZE, recordVideo: { dir: recordings, size: SIZE } })
  await context.addInitScript(() => localStorage.setItem('medfold-theme', 'light'))
  await context.addInitScript(overlay)
  const page = await context.newPage()
  const started = Date.now()
  let trimStart = 0

  const timings = []
  const caption = (step, text) => {
    // Video time: 4 s title card, then the recording from the moment the login page is ready.
    if (text) timings.push(`${(4 + (Date.now() - started) / 1000 - trimStart).toFixed(1).padStart(5)} s  ${text}`)
    return page.evaluate((html) => {
      sessionStorage.setItem('demo-caption', html)
      const el = document.getElementById('demo-caption')
      if (el) {
        el.innerHTML = html
        el.style.display = html ? 'flex' : 'none'
      }
    }, step ? `<b>${step}</b><span>${text}</span>` : text)
  }
  const moveTo = async (locator) => {
    const box = await locator.boundingBox()
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 28 })
    await pause(150)
  }
  const click = async (locator) => {
    await moveTo(locator)
    await locator.click()
  }

  // 1. Log in
  await page.goto(`${APP}/login`)
  await page.getByLabel('Email').waitFor()
  trimStart = (Date.now() - started) / 1000
  await page.mouse.move(900, 300)
  await caption('1', 'Log in to your private record')
  await pause(800)
  await click(page.getByLabel('Email'))
  await page.getByLabel('Email').pressSequentially(EMAIL, { delay: 35 })
  await click(page.getByLabel('Password'))
  await page.getByLabel('Password').pressSequentially(PASSWORD, { delay: 35 })
  await click(page.getByRole('button', { name: 'Log in' }))
  await page.getByRole('region', { name: 'Summary' }).waitFor()
  await caption('', 'Your record at a glance: what changed, and what is worth asking your doctor about')
  await pause(3500)
  await moveTo(page.getByRole('link', { name: 'HbA1c' }))
  await pause(3000)

  // 2. Upload
  await click(page.getByRole('link', { name: 'Documents', exact: true }))
  await page.getByRole('heading', { name: 'Your documents' }).waitFor()
  await caption('2', 'Upload a lab report: a PDF, a scan or a phone photo')
  const gifStart = (Date.now() - started) / 1000 - trimStart
  await moveTo(page.getByRole('button', { name: 'Choose files' }))
  await pause(1200)
  await page.locator('input[type=file]').setInputFiles(UPLOAD)
  await caption('', 'Medfold reads it in the background: the PDF text layer, or OCR for scans and photos, then AI extraction')
  const uploaded = page.getByRole('link', { name: 'Thyroid Profile' })
  await uploaded.waitFor({ timeout: 60000 })
  await pause(4000)

  // 3. What OCR + AI produced
  await click(uploaded)
  await page.getByRole('table').waitFor()
  await caption('3', 'Read and organised: type, date, doctor, and every value with the range printed on the report')
  await pause(4500)
  await page.mouse.move(800, 600, { steps: 20 })
  await page.mouse.wheel(0, 700)
  await caption('', 'TSH is above the printed range. That comparison is arithmetic in the backend, never an AI opinion')
  await pause(4500)
  const gifEnd = (Date.now() - started) / 1000 - trimStart

  // 4. Timeline
  await click(page.getByRole('link', { name: 'Documents', exact: true }))
  await page.getByRole('heading', { name: 'Your documents' }).waitFor()
  await caption('4', 'Timeline: every document by the date of the record, not the upload')
  await page.mouse.move(700, 500, { steps: 15 })
  await page.mouse.wheel(0, 420)
  await pause(1800)
  await click(page.getByRole('button', { name: /^Prescription/ }))
  await pause(1500)
  await click(page.getByRole('button', { name: 'All' }))
  await pause(1000)

  // Trends
  await click(page.getByRole('link', { name: 'Test results', exact: true }))
  await click(page.getByRole('link', { name: 'HbA1c' }))
  await page.locator('circle[tabindex="0"]').first().waitFor()
  await caption('', 'The same test from four reports, lined up over time, with units converted where needed')
  for (const i of [0, 1, 2, 3]) {
    await moveTo(page.locator('circle[tabindex="0"]').nth(i))
    await pause(700)
  }
  await pause(1200)

  // 5. Search
  await caption('5', 'Search any word in any document, from anywhere: Ctrl / ⌘ K')
  await pause(1000)
  await page.keyboard.press('Control+k')
  await page.getByRole('combobox').pressSequentially('metformin', { delay: 90 })
  await page.getByRole('option', { name: /Prescription/ }).waitFor()
  await pause(3500)
  await page.keyboard.press('Enter')
  await page.getByRole('heading', { name: 'Prescription', level: 1 }).waitFor()
  await caption('', 'The match opens the prescription it came from')
  await pause(3000)

  // Visit summary
  await click(page.getByRole('link', { name: 'Visit summary', exact: true }))
  await page.getByRole('table').waitFor()
  await caption('', 'One page for your next appointment: latest results, previous values, medicines. Print or save as PDF')
  await pause(2000)
  await page.mouse.move(800, 500, { steps: 15 })
  await page.mouse.wheel(0, 500)
  await pause(4500)

  // 6. Dark mode and log out
  await click(page.getByRole('button', { name: 'Switch to dark mode' }))
  await caption('', 'Dark mode, and it works on phones too')
  await pause(2200)
  await click(page.getByRole('button', { name: 'Switch to light mode' }))
  await caption('6', 'Log out: the session is revoked on the server, not just forgotten by the browser')
  await pause(1500)
  await click(page.getByRole('button', { name: 'Log out' }))
  await page.getByRole('heading', { name: 'Log in to Medfold' }).waitFor()
  await caption('', '')
  await pause(1800)

  const trimEnd = (Date.now() - started) / 1000
  const video = page.video()
  await context.close()
  const raw = await video.path()

  // Title and end cards.
  const cards = await browser.newPage({ viewport: SIZE })
  const cardFile = 'file://' + path.join(__dirname, 'card.html')
  await cards.goto(cardFile)
  await cards.screenshot({ path: path.join(recordings, 'title.png') })
  await cards.goto(cardFile + '?end')
  await cards.screenshot({ path: path.join(recordings, 'end.png') })
  await browser.close()

  const ff = (...args) => execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...args])
  const part = (name) => path.join(recordings, name)
  const enc = ['-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p', '-r', '30']
  ff('-loop', '1', '-t', '4', '-i', part('title.png'), '-vf', 'fade=t=in:st=0:d=0.6,fade=t=out:st=3.5:d=0.5', ...enc, part('title.mp4'))
  ff('-ss', String(trimStart), '-to', String(trimEnd), '-i', raw, '-vf', 'fade=t=in:st=0:d=0.4', ...enc, part('app.mp4'))
  ff('-loop', '1', '-t', '5', '-i', part('end.png'), '-vf', 'fade=t=in:st=0:d=0.6', ...enc, part('end.mp4'))
  fs.writeFileSync(part('list.txt'), ['title', 'app', 'end'].map((n) => `file '${part(n + '.mp4')}'`).join('\n'))
  ff('-f', 'concat', '-safe', '0', '-i', part('list.txt'), '-c', 'copy', '-movflags', '+faststart', path.join(OUT, 'demo-video.mp4'))

  // The README GIF: upload, processing and the extracted results, small enough to load quickly.
  ff('-ss', String(gifStart), '-to', String(gifEnd), '-i', part('app.mp4'), '-vf',
    'fps=8,scale=800:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle',
    path.join(OUT, 'quick-demo.gif'))
  fs.rmSync(recordings, { recursive: true })
  const seconds = execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path.join(OUT, 'demo-video.mp4')])
  console.log(`demo/demo-video.mp4: ${Number(seconds).toFixed(1)} s\nCaptions:\n${timings.join('\n')}`)
})()
