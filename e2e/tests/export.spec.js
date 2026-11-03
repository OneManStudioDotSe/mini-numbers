// @ts-check
const fs = require('fs');
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('report export (audit H5)', () => {
  test('the CSV report carries the revenue section and quoted raw events', async ({ page }, testInfo) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    // Enough projects that the one under test sits below the sidebar's sticky footer until scrolled
    for (let i = 0; i < 5; i++) await createProject(page.request, `filler ${Date.now()}-${i}`);
    const project = await createProject(page.request);
    const demo = await page.request.post(`/admin/projects/${project.id}/demo-data`, { data: { count: 300, timeScope: 7 } });
    expect(demo.status()).toBe(200);

    await page.goto('/admin-panel');
    const item = page.locator('.project-menu__item', { hasText: project.name });
    for (const theme of ['light', 'dark']) {
      const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
      if (current !== theme) await page.click('#theme-toggle');
      await item.scrollIntoViewIfNeeded();
      await page.screenshot({ path: testInfo.outputPath(`sidebar-long-list-${theme}.png`) });
    }
    await item.click(); // the last project in a long list must not sit under the footer
    await expect(page.locator('#total-views')).not.toHaveText(/^(0|-|)$/, { timeout: 15_000 });
    await expect.poll(() => page.evaluate(() => RevenueManager.state.stats?.totalRevenue), { timeout: 15_000 }).toBeGreaterThan(0);

    await page.click('#open-settings-btn');
    await expect(page.locator('#settings-modal')).toHaveClass(/show/);
    for (const cb of await page.locator('.export-section-cb').all()) await cb.check();
    const [download] = await Promise.all([page.waitForEvent('download'), page.click('#download-report-btn')]);
    const csv = fs.readFileSync(await download.path(), 'utf8');

    expect(csv).toMatch(/REVENUE\nTotal revenue,[0-9]*[1-9]/);
    expect(csv).toMatch(/\nEvent,Revenue,Transactions\n"purchase",/);
    const raw = csv.split('RAW EVENTS\n')[1];
    expect(raw, 'raw events section').toBeTruthy();
    const rows = raw.trim().split('\n');
    expect(rows[0]).toBe('Timestamp,Type,Path,Country,City,Browser,Device,Duration');
    expect(rows.length, 'all 300 demo events exported').toBe(301);
    expect(rows[1]).toMatch(/^"[^"]+","(pageview|custom|heartbeat)","\/[^"]*",("[^"]*",){4}\d+$/);
  });
});
