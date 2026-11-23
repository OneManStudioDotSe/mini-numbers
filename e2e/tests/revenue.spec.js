// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('revenue lab', () => {
  test('the card keeps each currency apart and shows refunds on their own', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    const project = await createProject(page.request);

    const purchase = async (properties, eventName = 'purchase') => {
      const r = await page.request.post('/collect', {
        headers: { 'X-Project-Key': project.apiKey },
        data: { path: '/checkout', sessionId: 'revenue-e2e', type: 'custom', eventName, properties: JSON.stringify(properties) },
      });
      expect(r.status()).toBe(202);
    };
    await purchase({ revenue: 100, currency: 'USD' });
    await purchase({ revenue: 50, currency: 'USD' });
    await purchase({ revenue: -30, currency: 'USD' }, 'refund');
    await purchase({ revenue: 40, currency: 'EUR' });
    await purchase({ revenue: 5, currency: 'ABCD' }); // not a currency code Intl knows

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.locator('[data-section="events"] .dashboard-section__header').click();

    const total = page.locator('#revenue-total');
    await expect(total.locator('.revenue-amount')).toHaveCount(3);
    await expect(total).toContainText('$120.00'); // 150 sold less 30 refunded, not mixed with the euros
    await expect(total).toContainText('€40.00');
    await expect(total).toContainText('5.00 ABCD');
    await expect(page.locator('#revenue-refunds')).toContainText('Refunds: -$30.00 (1)');
    await expect(page.locator('#revenue-transactions')).toHaveText('4'); // the refund is not a sale
    await expect(page.locator('#revenue-aov')).toContainText('$75.00');
    await expect(page.locator('#revenue-breakdown')).toContainText('refunded $30.00');
    await expect(page.locator('#revenue-attribution-body')).toContainText('€40.00');

    for (const theme of ['light', 'dark']) {
      const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
      if (current !== theme) await page.click('#theme-toggle');
      await expect(page.locator('#revenue-section')).toBeVisible();
      await page.locator('#revenue-section').screenshot({ path: test.info().outputPath(`revenue-${theme}.png`) });
    }
  });
});
