// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaUi, loginViaApi, createProject } = require('../helpers');

test.describe('dashboard', () => {
  test('creates a project through the UI and shows the tracking snippet', async ({ page }) => {
    await loginViaUi(page);
    await page.click('#new-project-btn');
    await expect(page.locator('#create-project-modal')).toHaveClass(/show/);
    const name = `UI project ${Date.now()}`;
    await page.fill('#project-name', name);
    await page.fill('#project-domain', 'ui.example.com');
    await page.click('#confirm-create-project');
    await expect(page.locator('#create-step-2')).toBeVisible();
    await expect(page.locator('#tracking-snippet-code')).toContainText('tracker.min.js');
    await expect(page.locator('#generated-api-key-final')).toHaveText(/^[0-9a-f]{32}$/);
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
});
