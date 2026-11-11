// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject, serveTrackedSite, waitForEvents } = require('../helpers');

test.describe('tracker.js on a tracked site', () => {
  // One signed-in API session for the whole file: the credential endpoints allow 20 logins per IP per minute
  let api;
  let project;
  test.beforeAll(async ({ playwright }) => {
    api = await playwright.request.newContext({ baseURL: `http://localhost:${process.env.E2E_PORT || '8080'}` });
    await loginViaApi(api);
  });
  test.afterAll(async () => { await api.dispose(); });
  test.beforeEach(async () => {
    project = await createProject(api);
  });

  test('records the initial pageview with the right path and referrer', async ({ page }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/pricing?utm_source=newsletter&utm_campaign=oct');
    const events = await waitForEvents(api, project.id, e => e.some(x => x.eventType === 'pageview'));
    const pv = events.find(x => x.eventType === 'pageview');
    expect(pv.path).toBe('/pricing');
    expect(pv.utmSource).toBe('newsletter');
    expect(pv.utmCampaign).toBe('oct');
    expect(pv.sessionId).toMatch(/^[0-9a-f]{32}$/);
  });

  test('the snippet exactly as the dashboard shows it sends events to the Mini Numbers server', async ({ page }) => {
    // The site (tracked.test) and Mini Numbers live on different origins, as on any real install
    await serveTrackedSite(page, project.apiKey, '', { snippetAsShown: true });
    await page.goto('http://tracked.test/welcome');
    const events = await waitForEvents(api, project.id, e => e.some(x => x.eventType === 'pageview'));
    expect(events.find(x => x.eventType === 'pageview')?.path).toBe('/welcome');
  });

  test('tracks SPA navigations through the History API and custom events with properties', async ({ page }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    await page.evaluate(() => history.pushState({}, '', '/checkout'));
    await page.evaluate(() => window.MiniNumbers.track('purchase', { revenue: 42.5, currency: 'EUR' }));
    const events = await waitForEvents(api, project.id, e =>
      e.some(x => x.path === '/checkout' && x.eventType === 'pageview') && e.some(x => x.eventType === 'custom'));
    const custom = events.find(x => x.eventType === 'custom');
    expect(custom.eventName).toBe('purchase');
    expect(JSON.parse(custom.properties)).toEqual({ revenue: 42.5, currency: 'EUR' });
    expect(events.filter(x => x.eventType === 'pageview').map(x => x.path).sort()).toEqual(['/', '/checkout']);
  });

  test('sends heartbeats while visible and uses the same session id', async ({ page }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    const events = await waitForEvents(api, project.id, e => e.some(x => x.eventType === 'heartbeat'), 40);
    const sessions = new Set(events.map(x => x.sessionId));
    expect(events.some(x => x.eventType === 'heartbeat')).toBe(true);
    expect(sessions.size).toBe(1);
  });

  test('queues events while offline and drains the queue when back online', async ({ page, context }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    await waitForEvents(api, project.id, e => e.length >= 1);
    await context.setOffline(true);
    await page.evaluate(() => window.MiniNumbers.track('offline_click'));
    await page.waitForFunction(() => (JSON.parse(localStorage.getItem('mn_queue') || '[]')).length >= 1);
    const queued = await page.evaluate(() => JSON.parse(localStorage.getItem('mn_queue')));
    expect(queued[0].eventName).toBe('offline_click');
    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    const events = await waitForEvents(api, project.id, e => e.some(x => x.eventName === 'offline_click'), 40);
    expect(events.some(x => x.eventName === 'offline_click')).toBe(true);
    await expect.poll(() => page.evaluate(() => localStorage.getItem('mn_queue'))).toBeNull();
  });

  test('detects outbound links and file downloads', async ({ page }) => {
    await serveTrackedSite(page, project.apiKey,
      '<a id="out" href="https://other.example.com/x">out</a> <a id="dl" href="/files/report.pdf">pdf</a>');
    await page.route('https://other.example.com/**', r => r.fulfill({ status: 200, body: 'ok' }));
    await page.route('http://tracked.test/files/**', r => r.fulfill({ status: 200, body: 'pdf' }));
    await page.goto('http://tracked.test/');
    await page.click('#out');
    await page.goBack().catch(() => {});
    await page.goto('http://tracked.test/');
    await page.click('#dl');
    const events = await waitForEvents(api, project.id, e =>
      e.some(x => x.eventType === 'outbound') && e.some(x => x.eventType === 'download'));
    expect(events.find(x => x.eventType === 'outbound').eventName).toBe('other.example.com');
    expect(events.find(x => x.eventType === 'download').eventName).toBe('report.pdf');
  });

  for (const [signal, init] of [
    ['Global Privacy Control', () => Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', { get: () => true })],
    ['Do Not Track', () => Object.defineProperty(Navigator.prototype, 'doNotTrack', { get: () => '1' })],
  ]) {
    test(`does not count a visitor whose browser sends ${signal}`, async ({ page }) => {
      await page.addInitScript(init);
      const collects = [];
      page.on('request', r => { if (new URL(r.url()).pathname === '/collect') collects.push(r.url()); });
      await serveTrackedSite(page, project.apiKey);
      await page.goto('http://tracked.test/private');
      await page.evaluate(() => window.MiniNumbers.track('ignored')); // still callable, does nothing
      await page.waitForTimeout(3000); // longer than the 2 s heartbeat used here
      expect(collects).toEqual([]);
    });
  }

  test('counts privacy-signal visitors when the site opts out of honouring them', async ({ page }) => {
    await page.addInitScript(() => Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', { get: () => true }));
    await serveTrackedSite(page, project.apiKey, '', { scriptAttrs: 'data-respect-privacy-signals="false"' });
    await page.goto('http://tracked.test/counted');
    const events = await waitForEvents(api, project.id, e => e.some(x => x.eventType === 'pageview'));
    expect(events.find(x => x.eventType === 'pageview')?.path).toBe('/counted');
  });
});
