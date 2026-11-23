// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

const AGENTS = {
  chrome: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  firefox: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0',
  safari: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
};

test.describe('segments', () => {
  test('a segment is created on an empty project, analysed, edited and deleted in the dashboard', async ({ page }, testInfo) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    const project = await createProject(page.request, `segments ${Date.now()}`);
    for (const [name, agent] of Object.entries(AGENTS)) {
      const r = await page.request.post(`/collect?key=${project.apiKey}`, {
        headers: { 'User-Agent': agent, 'Content-Type': 'text/plain' },
        data: JSON.stringify({ path: '/', sessionId: `seg-${name}`, type: 'pageview' }),
      });
      expect(r.status(), `${name} page view`).toBe(202);
    }

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.locator('[data-section="conversions"] .dashboard-section__header').click();

    // A project without segments still offers the way to create the first one
    const card = page.locator('#segments-section');
    await expect(card).toBeVisible();
    await expect(card).toContainText('No segments yet');
    await page.click('#manage-segments-btn');
    const modal = page.locator('#segments-modal');
    await expect(modal).toHaveClass(/show/);

    // Create: browser contains Chrome OR browser contains Firefox
    const rows = modal.locator('.segment-filter-row');
    await modal.locator('#create-segment-btn').click();
    await expect(page.locator('.toast').last()).toContainText('Segment name is required');
    await expect(modal, 'a refused save keeps the dialog open').toHaveClass(/show/);
    await page.fill('#segment-name', 'Chrome or Firefox');
    await page.fill('#segment-description', 'Two browsers');
    await rows.nth(0).locator('[data-filter="operator"]').selectOption('contains');
    await rows.nth(0).locator('[data-filter="value"]').fill('Chrome');
    await page.click('#add-segment-filter-btn');
    await rows.nth(1).locator('.segment-logic-select').selectOption('OR');
    await rows.nth(1).locator('[data-filter="operator"]').selectOption('contains');
    await rows.nth(1).locator('[data-filter="value"]').fill('Firefox');
    await modal.locator('#create-segment-btn').click();

    const tableRow = page.locator('#segments-table-body tr', { hasText: 'Chrome or Firefox' });
    await expect(tableRow).toBeVisible();
    await expect(tableRow).toContainText('OR');
    await expect(page.locator('#segment-name')).toHaveValue('');
    expect(await (await page.request.get(`/admin/projects/${project.id}/segments`)).json()).toHaveLength(1);

    for (const theme of ['light', 'dark']) {
      if ((await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light')) !== theme) {
        await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      }
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await page.screenshot({ path: testInfo.outputPath(`segments-modal-${theme}.png`) });
    }

    // The dashboard card lists it; Analyze counts the Chrome and the Firefox visit, not the Safari one
    await page.click('#close-segments-modal');
    await expect(card.locator('.segment-card')).toContainText('Chrome or Firefox');
    const result = page.locator('#segment-result');
    await expect(result).toBeHidden();
    await card.locator('[data-action="analyze-segment"]').click();
    await expect(result).toBeVisible();
    await expect(result.locator('#segment-result-title')).toHaveText('Chrome or Firefox: result');
    await expect(result.locator('#segment-result-views')).toHaveText('2');
    await expect(result.locator('#segment-result-visitors')).toHaveText('2');
    await expect(result.locator('#segment-result-bounce')).toHaveText('100.0%');
    await expect(result.locator('#segment-result-pages tr')).toHaveCount(1);
    await expect(result.locator('#segment-result-pages tr')).toContainText('/');

    for (const theme of ['light', 'dark']) {
      await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await result.screenshot({ path: testInfo.outputPath(`segment-result-${theme}.png`) });
    }

    // Re-run keeps the panel; closing hides it
    await result.locator('#segment-result-rerun').click();
    await expect(result.locator('#segment-result-views')).toHaveText('2');
    await result.locator('#segment-result-close').click();
    await expect(result).toBeHidden();

    // Edit: the form is filled from the segment; switch OR to AND and rename
    await page.click('#manage-segments-btn');
    await tableRow.locator('[data-action="edit-segment"]').click();
    await expect(page.locator('#segment-form-title')).toHaveText('Edit segment');
    await expect(page.locator('#cancel-segment-edit-btn')).toBeVisible();
    await expect(page.locator('#segment-name')).toHaveValue('Chrome or Firefox');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(1).locator('.segment-logic-select')).toHaveValue('OR');
    await expect(rows.nth(1).locator('[data-filter="value"]')).toHaveValue('Firefox');
    await rows.nth(1).locator('.segment-logic-select').selectOption('AND');
    await page.fill('#segment-name', 'Chrome and Firefox');
    await modal.locator('#create-segment-btn').click();
    const edited = page.locator('#segments-table-body tr', { hasText: 'Chrome and Firefox' });
    await expect(edited).toBeVisible();
    await expect(page.locator('#segments-table-body tr')).toHaveCount(1);
    await expect(page.locator('#segment-form-title')).toHaveText('Create new segment');
    await expect(page.locator('#cancel-segment-edit-btn')).toBeHidden();

    await page.click('#close-segments-modal');
    await card.locator('[data-action="analyze-segment"]').click();
    await expect(result.locator('#segment-result-title')).toHaveText('Chrome and Firefox: result');
    await expect(result.locator('#segment-result-views')).toHaveText('0');
    await expect(result.locator('#segment-result-pages')).toContainText('No matching page views');

    // Delete
    await page.click('#manage-segments-btn');
    page.once('dialog', d => d.accept());
    await edited.locator('[data-action="delete-segment"]').click();
    await expect(page.locator('#segments-table-body')).toContainText('No segments yet');
    await page.click('#close-segments-modal');
    await expect(card).toContainText('No segments yet');
  });
});
