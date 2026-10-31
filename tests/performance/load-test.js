// Load test for the Medfold API with k6 (https://k6.io).
//
//   docker run --rm -i --network host --user "$(id -u)" -v "$PWD/tests:/tests" grafana/k6:2.3.0 run \
//     --summary-export=/tests/performance/results.json /tests/performance/load-test.js
//
// Against another address: -e API_URL=http://localhost:3000/api/v1
// Setup creates a fresh account and processes the sample reports; then 20 virtual users browse
// (timeline, results, trend, search) for a minute while a separate scenario keeps uploading new
// documents and measures the time until each is READY.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const API = __ENV.API_URL || 'http://localhost:8080/api/v1';
const REPORTS = [
  'complete-blood-count-2026-03-02.pdf',
  'metabolic-panel-2024-11-08.pdf',
  'metabolic-panel-2025-05-14.pdf',
  'metabolic-panel-2025-11-20.pdf',
  'metabolic-panel-2026-05-18.pdf',
  'prescription-2026-05-20.pdf',
];
const FILES = Object.fromEntries(REPORTS.map((name) => [name, open(`../sample-pdfs/${name}`, 'b')]));
const readyAfterUpload = new Trend('upload_to_ready', true);

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      exec: 'browse',
      stages: [
        { duration: '10s', target: 20 },
        { duration: '45s', target: 20 },
        { duration: '5s', target: 0 },
      ],
    },
    upload: {
      executor: 'constant-arrival-rate',
      exec: 'upload',
      rate: 1,
      timeUnit: '3s',
      duration: '55s',
      preAllocatedVUs: 4,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:timeline}': ['p(95)<200'],
    'http_req_duration{endpoint:results}': ['p(95)<200'],
    'http_req_duration{endpoint:trend}': ['p(95)<200'],
    'http_req_duration{endpoint:search}': ['p(95)<300'],
    'http_req_duration{endpoint:upload}': ['p(95)<500'],
    upload_to_ready: ['p(95)<10000'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

const json = { headers: { 'Content-Type': 'application/json' } };
const auth = (token, tags) => ({ headers: { Authorization: `Bearer ${token}` }, tags });

function waitUntilReady(token, id, timeoutSeconds = 60) {
  for (let i = 0; i < timeoutSeconds * 5; i++) {
    const res = http.get(`${API}/documents/${id}/status`, auth(token, { endpoint: 'status' }));
    const status = res.json('status');
    if (status === 'READY' || status === 'FAILED') return status;
    sleep(0.2);
  }
  return 'TIMEOUT';
}

function uploadFile(token, name, bytes) {
  return http.post(`${API}/documents`, { file: http.file(bytes, name, 'application/pdf') }, auth(token, { endpoint: 'upload' }));
}

export function setup() {
  const email = `load-${Date.now()}@example.com`;
  const res = http.post(`${API}/auth/register`, JSON.stringify({ email, password: 'load-test-password' }), json);
  check(res, { registered: (r) => r.status === 201 });
  const token = res.json('accessToken');
  const ids = REPORTS.map((name) => uploadFile(token, name, FILES[name]).json('id'));
  ids.forEach((id) => waitUntilReady(token, id));
  return { token, ids };
}

export function browse({ token, ids }) {
  const id = ids[Math.floor(Math.random() * ids.length)];
  check(http.get(`${API}/documents?size=20`, auth(token, { endpoint: 'timeline' })), { timeline: (r) => r.status === 200 });
  check(http.get(`${API}/documents/${id}/results`, auth(token, { endpoint: 'results' })), { results: (r) => r.status === 200 });
  check(http.get(`${API}/labs/hba1c`, auth(token, { endpoint: 'trend' })), { trend: (r) => r.status === 200 });
  check(http.get(`${API}/search?q=haemoglobin`, auth(token, { endpoint: 'search' })), { search: (r) => r.status === 200 });
  sleep(0.5);
}

export function upload({ token }) {
  // A trailing PDF comment makes every upload a new file (duplicates are rejected by hash).
  const name = REPORTS[Math.floor(Math.random() * REPORTS.length)];
  const unique = new Uint8Array([...new Uint8Array(FILES[name]), ...new TextEncoder().encode(`\n% ${__VU}-${__ITER}-${Date.now()}\n`)]);
  const started = Date.now();
  const res = uploadFile(token, name, unique.buffer);
  if (!check(res, { uploaded: (r) => r.status === 201 })) return;
  const status = waitUntilReady(token, res.json('id'));
  check(status, { processed: (s) => s === 'READY' });
  readyAfterUpload.add(Date.now() - started);
}
