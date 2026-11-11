// @ts-check
const { test, expect } = require('@playwright/test');

/** The unconfigured second server from playwright.config.js. */
const WIZARD = `http://localhost:${Number(process.env.E2E_PORT || '8080') + 1}`;

test.describe('setup wizard', () => {
  test('Quick Setup generates a password, shows it once and waits for launch', async ({ page, context }, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: WIZARD });
    await page.goto(`${WIZARD}/`);
    await expect(page).toHaveURL(/\/setup/);

    await page.click('#btn-quick-setup');
    const shown = page.locator('#review-password-value');
    await expect(shown).toBeVisible();
    const password = (await shown.innerText()).trim();
    expect(password).toMatch(/^[A-HJ-NP-Za-km-z2-9]{20}$/);
    await expect(page.locator('#review-content')).toContainText(/Dev Mode\s*Enabled/); // served over plain HTTP

    // Nothing is saved until the user launches
    await page.waitForTimeout(1500);
    const status = await (await page.request.get(`${WIZARD}/setup/api/status`)).json();
    expect(status.setupNeeded, 'Quick Setup must not submit by itself').toBe(true);

    await page.click('#btn-copy-password');
    await expect(page.locator('#btn-copy-password')).toHaveText('Copied');
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(password);

    for (const theme of ['light', 'dark']) {
      if ((await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light')) !== theme) await page.click('#theme-toggle');
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await page.screenshot({ path: testInfo.outputPath(`wizard-review-${theme}.png`) });
    }

    await page.click('#btn-submit');
    await expect(page.locator('#success-modal')).toBeVisible({ timeout: 15_000 });

    // The generated password is the admin's password
    await page.goto(`${WIZARD}/login`);
    await page.fill('#username', 'admin');
    await page.fill('#password', password);
    await page.click('#login-btn');
    await page.waitForURL(/admin-panel/);
  });
});
