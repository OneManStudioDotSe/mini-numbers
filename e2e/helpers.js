// @ts-check
const { expect } = require('@playwright/test');

const ADMIN = { username: 'admin', password: 'E2e-password-123' };

/** Log in through the real login page and wait for the dashboard. */
async function loginViaUi(page) {
  await page.goto('/login');
  await page.fill('#username', ADMIN.username);
  await page.fill('#password', ADMIN.password);
  await page.click('#login-btn');
  await page.waitForURL(/admin-panel/);
}

/** Log in on the API request context shared by the page (same cookie jar). */
async function loginViaApi(request) {
  const r = await request.post('/api/login', { data: ADMIN });
  expect(r.status(), 'API login').toBe(200);
}

/** Create a project via the API and return { id, name, apiKey }. */
async function createProject(request, name = `e2e ${Date.now()}`) {
  const created = await request.post('/admin/projects', { data: { name, domain: 'tracked.test' } });
  expect(created.status(), 'create project').toBe(201);
  const listed = await (await request.get('/admin/projects')).json();
  const projects = Array.isArray(listed) ? listed : (listed.items || listed.data || []);
  const project = projects.find(p => p.name === name);
  expect(project, 'project appears in the list').toBeTruthy();
  return project;
}

/** Fetch raw events for a project (newest first). */
async function rawEvents(request, projectId) {
  const r = await request.get(`/admin/projects/${projectId}/events?page=0&limit=50`);
  expect(r.status()).toBe(200);
  return (await r.json()).events;
}

/**
 * A fake tracked site served from a real origin via request interception, so the
 * tracker runs exactly as it would on a customer's page (document.currentScript,
 * sendBeacon, sessionStorage, History API). See playwright.config.js for why Chrome's
 * local-network-access check is disabled for these runs.
 */
async function serveTrackedSite(page, apiKey, extraHtml = '') {
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Tracked site</title>
    <script defer src="http://localhost:8080/tracker/tracker.min.js"
      data-project-key="${apiKey}" data-api-endpoint="http://localhost:8080/collect"
      data-heartbeat-interval="2000"></script></head>
    <body><h1>Tracked</h1>${extraHtml}<div style="height:3000px"></div></body></html>`;
  page.on('console', m => { if (m.type() === 'error' && !/ERR_INTERNET_DISCONNECTED/.test(m.text())) console.log(`[tracked page console] ${m.text()}`); });
  page.on('requestfailed', r => {
    const reason = r.failure()?.errorText;
    if (reason !== 'net::ERR_INTERNET_DISCONNECTED') console.log(`[tracked page request failed] ${r.url()} ${reason}`);
  });
  await page.route('http://tracked.test/**', route => route.fulfill({ status: 200, contentType: 'text/html', body: html }));
}

async function waitForEvents(request, projectId, predicate, attempts = 20) {
  for (let i = 0; i < attempts; i++) {
    const events = await rawEvents(request, projectId);
    if (predicate(events)) return events;
    await new Promise(r => setTimeout(r, 250));
  }
  return rawEvents(request, projectId);
}

module.exports = { ADMIN, loginViaUi, loginViaApi, createProject, rawEvents, serveTrackedSite, waitForEvents };
