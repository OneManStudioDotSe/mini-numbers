// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaUi, ADMIN } = require('../helpers');

test.describe('authentication', () => {
  test('root redirects to the login page and a wrong password is rejected in place', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
    await page.fill('#username', ADMIN.username);
    await page.fill('#password', 'definitely-wrong');
    await page.click('#login-btn');
    await expect(page.locator('#error-message')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('a valid login reaches the dashboard and sign-out returns to login', async ({ page }) => {
    await loginViaUi(page);
    await expect(page.locator('#sidebar')).toBeVisible();
    await page.click('#sign-out-btn');
    await expect(page.locator('#logout-modal')).toHaveClass(/show/);
    await page.click('#confirm-logout');
    await expect(page).toHaveURL(/\/login/);
    // The session is really gone: the admin API now refuses the browser.
    const r = await page.request.get('/admin/projects');
    expect(r.status()).toBe(401);
  });
});
