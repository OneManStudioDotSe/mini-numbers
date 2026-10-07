// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject, serveTrackedSite, waitForEvents } = require('../helpers');

test.describe('tracker.js on a tracked site', () => {
  let project;
  test.beforeEach(async ({ request }) => {
    await loginViaApi(request);
    project = await createProject(request);
  });

  test('records the initial pageview with the right path and referrer', async ({ page, request }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/pricing?utm_source=newsletter&utm_campaign=oct');
    const events = await waitForEvents(request, project.id, e => e.some(x => x.eventType === 'pageview'));
    const pv = events.find(x => x.eventType === 'pageview');
    expect(pv.path).toBe('/pricing');
    expect(pv.utmSource).toBe('newsletter');
    expect(pv.utmCampaign).toBe('oct');
    expect(pv.sessionId).toMatch(/^[0-9a-f]{32}$/);
  });

  test('tracks SPA navigations through the History API and custom events with properties', async ({ page, request }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    await page.evaluate(() => history.pushState({}, '', '/checkout'));
    await page.evaluate(() => window.MiniNumbers.track('purchase', { revenue: 42.5, currency: 'EUR' }));
    const events = await waitForEvents(request, project.id, e =>
      e.some(x => x.path === '/checkout' && x.eventType === 'pageview') && e.some(x => x.eventType === 'custom'));
    const custom = events.find(x => x.eventType === 'custom');
    expect(custom.eventName).toBe('purchase');
    expect(JSON.parse(custom.properties)).toEqual({ revenue: 42.5, currency: 'EUR' });
    expect(events.filter(x => x.eventType === 'pageview').map(x => x.path).sort()).toEqual(['/', '/checkout']);
  });

  test('sends heartbeats while visible and uses the same session id', async ({ page, request }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    const events = await waitForEvents(request, project.id, e => e.some(x => x.eventType === 'heartbeat'), 40);
    const sessions = new Set(events.map(x => x.sessionId));
    expect(events.some(x => x.eventType === 'heartbeat')).toBe(true);
    expect(sessions.size).toBe(1);
  });

  test('queues events while offline and drains the queue when back online', async ({ page, context, request }) => {
    await serveTrackedSite(page, project.apiKey);
    await page.goto('http://tracked.test/');
    await waitForEvents(request, project.id, e => e.length >= 1);
    await context.setOffline(true);
    await page.evaluate(() => window.MiniNumbers.track('offline_click'));
    await page.waitForFunction(() => (JSON.parse(localStorage.getItem('mn_queue') || '[]')).length >= 1);
    const queued = await page.evaluate(() => JSON.parse(localStorage.getItem('mn_queue')));
    expect(queued[0].eventName).toBe('offline_click');
    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    const events = await waitForEvents(request, project.id, e => e.some(x => x.eventName === 'offline_click'), 40);
    expect(events.some(x => x.eventName === 'offline_click')).toBe(true);
    await expect.poll(() => page.evaluate(() => localStorage.getItem('mn_queue'))).toBeNull();
  });

  test('detects outbound links and file downloads', async ({ page, request }) => {
    await serveTrackedSite(page, project.apiKey,
      '<a id="out" href="https://other.example.com/x">out</a> <a id="dl" href="/files/report.pdf">pdf</a>');
    await page.route('https://other.example.com/**', r => r.fulfill({ status: 200, body: 'ok' }));
    await page.route('http://tracked.test/files/**', r => r.fulfill({ status: 200, body: 'pdf' }));
    await page.goto('http://tracked.test/');
    await page.click('#out');
    await page.goBack().catch(() => {});
    await page.goto('http://tracked.test/');
    await page.click('#dl');
    const events = await waitForEvents(request, project.id, e =>
      e.some(x => x.eventType === 'outbound') && e.some(x => x.eventType === 'download'));
    expect(events.find(x => x.eventType === 'outbound').eventName).toBe('other.example.com');
    expect(events.find(x => x.eventType === 'download').eventName).toBe('report.pdf');
  });
});
