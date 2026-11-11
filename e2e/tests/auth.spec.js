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
    await expect(page.locator('#account-username')).toHaveText(username);
    await expect(page.locator('#change-password-btn')).toBeVisible();
    await expect(page.locator('#manage-users-btn')).toBeHidden();
    await page.click('#save-settings');
    await page.waitForTimeout(500);
    expect(forbidden, 'nothing the viewer did was refused').toEqual([]);
    await context.close();
  });

  // Every sign-in and password change counts toward the credential limit (20 per IP per minute), so this test spends
  // as few as it can: one admin sign-in shared by the page and its API requests.
  test('an admin adds a user in Settings, who signs in with the shown password and changes it', async ({ browser, page }) => {
    await loginViaApi(page.request);
    await createProject(page.request);
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await page.goto('/admin-panel');
    await page.click('#open-settings-btn');
    await expect(page.locator('#account-username')).toHaveText(ADMIN.username);
    await page.click('#manage-users-btn');
    await expect(page.locator('#users-modal')).toHaveClass(/show/);
    await expect(page.locator('#users-list tr:has(.badge) td').first()).toHaveText(`${ADMIN.username} you`);

    const username = `teammate${Date.now()}`;
    await page.fill('#new-user-name', username);
    await page.click('#add-user-btn');
    await expect(page.locator('#new-user-password')).toBeVisible();
    const password = await page.locator('#new-user-password-value').textContent();
    expect(password).toMatch(/^[A-Za-z2-9]{20}$/);
    const row = page.locator('#users-list tr', { hasText: username });
    await expect(row).toContainText('Active');
    await row.locator('select').selectOption('admin');
    await expect(page.locator('.toast', { hasText: 'Role changed' })).toBeVisible();
    await row.locator('select').selectOption('viewer');

    // The new user signs in with the shown password and changes it
    const context = await browser.newContext();
    const teammate = await context.newPage();
    await teammate.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await teammate.goto('/login');
    await teammate.fill('#username', username);
    await teammate.fill('#password', password);
    await teammate.click('#login-btn');
    await teammate.waitForURL(/admin-panel/);
    await teammate.click('#open-settings-btn');
    await teammate.click('#change-password-btn');
    await teammate.fill('#current-password', 'not-the-password');
    await teammate.fill('#new-password', 'Chosen-pass-456');
    await teammate.fill('#repeat-password', 'Chosen-pass-456');
    await teammate.click('#submit-change-password');
    await expect(teammate.locator('#change-password-error')).toHaveText('Current password is incorrect');
    await teammate.fill('#current-password', password);
    await teammate.click('#submit-change-password');
    await teammate.waitForURL(/\/login/);
    expect((await teammate.request.get('/admin/projects')).status(), 'signed out').toBe(401);
    await context.close();

    // Deactivate, then remove
    page.on('dialog', d => d.accept());
    await row.getByRole('button', { name: 'Deactivate' }).click();
    await expect(row).toContainText('Deactivated');
    await row.getByRole('button', { name: `Remove ${username}` }).click();
    await expect(page.locator('#users-list tr', { hasText: username })).toHaveCount(0);
  });
});
