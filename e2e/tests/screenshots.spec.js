// @ts-check
// Produces the README / docs screenshots from a realistic-looking demo project.
// Run on demand:  npx playwright test tests/screenshots.spec.js
// Output goes to ../docs/screenshots (committed) so the README can embed them.
const { test, expect } = require('@playwright/test');
const path = require('path');
const { loginViaUi, loginViaApi, createProject } = require('../helpers');

const OUT = path.resolve(__dirname, '../../docs/screenshots');

test('capture dashboard screenshots for the README', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await loginViaApi(page.request);
  const project = await createProject(page.request, 'Acme Store');
  const demo = await page.request.post(`/admin/projects/${project.id}/demo-data`, { data: { count: 1500, timeScope: 30 } });
  expect(demo.status()).toBe(200);

  await loginViaUi(page);
  await page.locator('.project-menu__item', { hasText: 'Acme Store' }).click();
  await expect(page.locator('#view-raw-events-btn')).toBeVisible();
  await expect(page.locator('#total-views')).not.toHaveText(/^(0|-|)$/, { timeout: 15_000 });
  await page.selectOption('#time-filter', '30d').catch(() => {});
  await page.waitForTimeout(1500);

  for (const theme of ['light', 'dark']) {
    const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
    if (current !== theme) await page.click('#theme-toggle');
    await expect.poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light')).toBe(theme);
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, `overview-${theme}.png`), clip: { x: 0, y: 0, width: 1440, height: 1000 } });
  }
  await page.click('#privacy-chip');
  await expect(page.locator('#privacy-modal')).toHaveClass(/show/);
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, 'privacy-posture-dark.png'), clip: { x: 0, y: 0, width: 1440, height: 1000 } });
});
