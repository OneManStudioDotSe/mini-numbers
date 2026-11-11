// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaUi, loginViaApi, createProject, serveTrackedSite, waitForEvents } = require('../helpers');

test.describe('dashboard', () => {
  test('creates a project through the UI; the snippet it shows really collects', async ({ page }) => {
    // On an empty database the onboarding dialog opens first and would cover the button
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaUi(page);
    const request = page.request; // same cookie jar as the logged-in page
    await page.click('#new-project-btn');
    await expect(page.locator('#create-project-modal')).toHaveClass(/show/);
    const name = `UI project ${Date.now()}`;
    await page.fill('#project-name', name);
    await page.fill('#project-domain', 'ui.example.com');
    await page.click('#confirm-create-project');
    await expect(page.locator('#create-step-2')).toBeVisible();
    await expect(page.locator('#tracking-snippet-code')).toContainText('tracker.min.js');
    const shownKey = (await page.locator('#generated-api-key-final').innerText()).trim();
    expect(shownKey).toMatch(/^[0-9a-f]{32}$/);

    // The key in the dialog must be the one the server stored (audit item C2)
    const projects = await (await request.get('/admin/projects')).json();
    const project = projects.find(p => p.name === name);
    expect(project.apiKey, 'dialog key equals stored key').toBe(shownKey);

    // "Go to dashboard" selects the new project
    await page.click('#confirm-create-project');
    await expect(page.locator('#create-project-modal')).not.toHaveClass(/show/);
    await expect.poll(() => page.evaluate(() => Dashboard.state.currentProjectId)).toBe(project.id);

    // A page carrying the shown snippet records a pageview
    await serveTrackedSite(page, shownKey, '', { snippetAsShown: true });
    await page.goto('http://tracked.test/welcome');
    const events = await waitForEvents(request, project.id, e => e.some(x => x.eventType === 'pageview'));
    expect(events.some(x => x.eventType === 'pageview' && x.path === '/welcome')).toBe(true);
  });

  test('renders demo data, opens Raw events as an accessible dialog, and works in both themes', async ({ page, request }, testInfo) => {
    await loginViaApi(page.request);
    const project = await createProject(page.request);
    const demo = await page.request.post(`/admin/projects/${project.id}/demo-data`, { data: { count: 200, timeScope: 7 } });
    expect(demo.status()).toBe(200);

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await expect(page.locator('#view-raw-events-btn')).toBeVisible();
    await expect(page.locator('#total-views')).not.toHaveText(/^(0|-|)$/, { timeout: 15_000 });

    // AI assistant referrals card (demo data includes assistant referrers)
    await expect(page.locator('#ai-referrals')).toHaveText(/^[0-9,]+$/, { timeout: 15_000 });
    await expect(page.locator('#ai-referrals-detail')).toContainText(/% of referred visits|No visits from/);

    // Privacy posture chip opens an accessible dialog with the three lists
    const chip = page.locator('#privacy-chip');
    await expect(chip).toContainText(/Standard · visitors forgotten after 1 day/);
    await chip.click();
    const privacyModal = page.locator('#privacy-modal');
    await expect(privacyModal).toHaveClass(/show/);
    await expect(privacyModal).toHaveAttribute('role', 'dialog');
    await expect(page.locator('#privacy-stored li')).toHaveCount(6);
    await expect(page.locator('#privacy-never li').first()).toContainText('IP address');
    await expect(page.locator('#privacy-forgotten')).toContainText('rotates every 1 day');
    await page.screenshot({ path: 'screenshots/privacy-modal.png' });
    await page.keyboard.press('Escape');
    await expect(privacyModal).not.toHaveClass(/show/);
    await expect(chip).toBeFocused();

    for (const theme of ['light', 'dark']) {
      const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
      if (current !== theme) await page.click('#theme-toggle');
      await expect.poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light')).toBe(theme);
      await page.screenshot({ path: testInfo.outputPath(`dashboard-${theme}.png`), fullPage: true });
      await page.screenshot({ path: `screenshots/dashboard-${theme}.png`, fullPage: true });
    }

    const trigger = page.locator('#view-raw-events-btn');
    await trigger.click();
    const modal = page.locator('#raw-events-modal');
    await expect(modal).toHaveClass(/show/);
    await expect(modal).toHaveAttribute('role', 'dialog');
    await expect(modal).toHaveAttribute('aria-modal', 'true');
    await expect(page.locator('#raw-events-tbody tr').first()).not.toContainText(/Loading|No events/);

    // Focus stays inside the dialog while tabbing
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('Tab');
      const inside = await page.evaluate(() => document.getElementById('raw-events-modal').contains(document.activeElement));
      expect(inside, `focus escaped the dialog on Tab #${i + 1}`).toBe(true);
    }
    await page.screenshot({ path: 'screenshots/raw-events-dark.png' });
    await page.keyboard.press('Escape');
    await expect(modal).not.toHaveClass(/show/);
    await expect(trigger).toBeFocused();
  });

  test('collapses the sidebar behind a menu button on a phone viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await loginViaUi(page);
    const sidebar = page.locator('#sidebar');
    await expect(page.locator('#menu-toggle')).toBeVisible();
    await expect(sidebar).not.toHaveClass(/open/);
    await expect(sidebar).not.toBeInViewport();
    await page.click('#menu-toggle');
    await expect(sidebar).toHaveClass(/open/);
    await expect(sidebar).toBeInViewport();
    await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390);
  });

  test('labs that are off leave no trace in the dashboard', async ({ page }) => {
    await loginViaApi(page.request);
    const project = await createProject(page.request);
    await page.request.post(`/admin/projects/${project.id}/demo-data`, { data: { count: 100, timeScope: 7 } });
    // The suite's server has every lab on; answer as a default server (LABS unset) would
    await page.route('**/admin/labs', route => route.fulfill({ json: { enabled: [] } }));
    const labCalls = [];
    page.on('request', r => { if (/\/(funnels|segments|revenue|webhooks|email-reports)(\/|\?|$)/.test(new URL(r.url()).pathname)) labCalls.push(r.url()); });

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await expect(page.locator('#total-views')).not.toHaveText(/^(0|-|)$/, { timeout: 15_000 });
    await expect(page.locator('#goals-section')).toBeAttached();
    for (const id of ['#funnels-section', '#segments-section', '#revenue-section', '#manage-webhooks-btn', '#manage-email-reports-btn']) {
      await expect(page.locator(id), id).toHaveCount(0);
    }
    await expect(page.locator('[data-section="events"] h2')).toHaveText('Events');
    await expect(page.locator('[data-section="conversions"] p')).toHaveText('Conversion goals with completion rates.');
    expect(labCalls, 'no requests to the API of a lab that is off').toEqual([]);
  });

  test('filter values, the country drill-down and the globe follow the data', async ({ page }) => {
    await loginViaApi(page.request);
    const project = await createProject(page.request);
    await page.request.post(`/admin/projects/${project.id}/demo-data`, { data: { count: 300, timeScope: 7 } });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await expect(page.locator('#total-views')).not.toHaveText(/^(0|-|)$/, { timeout: 15_000 });

    // "Filter by" values carry their counts
    await page.selectOption('#filter-dimension', 'browser');
    await expect.poll(() => page.locator('#filter-value option').count()).toBeGreaterThan(1);
    const options = await page.$$eval('#filter-value option', os => os.slice(1).map(o => o.textContent));
    for (const o of options) expect(o).toMatch(/\(\d+\)$/);
    await page.selectOption('#filter-dimension', '');

    // While drilled into a country, a period change still updates the other charts
    const points = () => page.evaluate(() => Object.values(Chart.instances).find(c => c.canvas.closest('#chart-timeseries') || c.canvas.id === 'chart-timeseries')?.data.labels.length);
    const report24h = () => page.waitForResponse(r => r.url().includes('/report/comparison') && r.url().includes('filter=24h'));
    const country = await page.evaluate(() => Dashboard.state.data.current.countries[0].label);
    await page.evaluate(c => Dashboard.drillDownToCountry(c), country);
    await expect(page.locator('#geo-title')).toHaveText(`Regions in ${country}`);
    await Promise.all([report24h(), page.selectOption('#time-filter', '24h')]);
    const expected = await page.evaluate(() => Dashboard.state.data.timeSeries.length);
    await expect.poll(points).toBe(expected);
    await expect(page.locator('#geo-title')).toHaveText(`Regions in ${country}`);

    // The globe loads its pinned script (a wrong integrity hash would refuse it)
    await page.locator('#geo-back-btn').dispatchEvent('click');
    await page.locator('#geo-view-toggle [data-view="globe"]').dispatchEvent('click');
    await expect.poll(() => page.evaluate(() => typeof window.Globe), { timeout: 20_000 }).toBe('function');
    expect(errors).toEqual([]);
  });
});
