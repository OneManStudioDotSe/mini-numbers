// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaUi, loginViaApi, createProject, ADMIN } = require('../helpers');

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

  test('a viewer sees the numbers but no controls that change them', async ({ browser, request }) => {
    await loginViaApi(request);
    const project = await createProject(request);
    const username = `viewer${Date.now()}`;
    const created = await request.post('/admin/users', { data: { username, password: 'Viewer-pass-123', role: 'viewer' } });
    expect(created.status()).toBe(201);

    const context = await browser.newContext();
    const page = await context.newPage();
    const forbidden = [];
    page.on('response', r => { if (r.status() === 403) forbidden.push(new URL(r.url()).pathname); });
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await page.goto('/login');
    await page.fill('#username', username);
    await page.fill('#password', 'Viewer-pass-123');
    await page.click('#login-btn');
    await page.waitForURL(/admin-panel/);
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await expect(page.locator('#view-raw-events-btn')).toBeVisible();

    for (const id of ['#new-project-btn', '#demo-project-btn', '#generate-demo-btn', '#manage-goals-btn']) {
      await expect(page.locator(id), id).toBeHidden();
    }
    await expect(page.locator('.project-menu__item', { hasText: project.name }).locator('.project-menu__delete')).toBeHidden();
    await page.click('#open-settings-btn');
    await expect(page.locator('#rotate-project-api-key')).toBeHidden();
    await expect(page.locator('#setting-project-name')).toHaveAttribute('readonly', '');
    await page.click('#save-settings');
    await page.waitForTimeout(500);
    expect(forbidden, 'nothing the viewer did was refused').toEqual([]);
    await context.close();
  });
});
