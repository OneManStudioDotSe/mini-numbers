// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('webhooks', () => {
  test('a webhook is created, paused, resumed and deleted in the dialog', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    // The dialog confirms deletion and reports errors with native dialogs
    const dialogs = [];
    page.on('dialog', d => { dialogs.push(d.message()); d.accept(); });
    await loginViaApi(page.request);
    const project = await createProject(page.request, `webhooks ${Date.now()}`);

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.click('#open-settings-btn');
    await page.click('#manage-webhooks-btn');
    const modal = page.locator('#webhooks-modal');
    await expect(modal).toHaveClass(/show/);
    await expect(modal.locator('#webhooks-table-body')).toContainText('No webhooks yet');

    // A link-local address is refused by the server and nothing is stored
    await page.fill('#webhook-url', 'https://169.254.169.254/latest');
    await page.click('#create-webhook-btn');
    await expect.poll(() => dialogs.join()).toContain('link-local');
    expect(await (await page.request.get(`/admin/projects/${project.id}/webhooks`)).json()).toHaveLength(0);

    // Create: the signing secret is shown once and the row appears
    await page.fill('#webhook-url', 'https://hooks.example.com/in');
    await page.click('#create-webhook-btn');
    await expect(page.locator('#webhook-secret-display .webhook-secret-value')).toHaveText(/^[0-9a-f]{64}$/);
    const row = modal.locator('#webhooks-table-body tr', { hasText: 'hooks.example.com' });
    await expect(row).toContainText('Active');
    await expect(row).toContainText('goal_conversion');
    await expect(row).toContainText('traffic_spike');
    await expect(page.locator('#webhook-url')).toHaveValue('');

    for (const theme of ['light', 'dark']) {
      if ((await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light')) !== theme) {
        await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      }
      await expect(row).toBeVisible();
    }

    // Pause, then resume
    await row.locator('button[title="Pause"]').click();
    await expect(row).toContainText('Paused');
    expect((await (await page.request.get(`/admin/projects/${project.id}/webhooks`)).json())[0].isActive).toBe(false);
    await row.locator('button[title="Resume"]').click();
    await expect(row).toContainText('Active');

    // Delete
    await row.locator('button[title="Delete"]').click();
    await expect(modal.locator('#webhooks-table-body')).toContainText('No webhooks yet');
    expect(dialogs.some(m => m.includes('Delete this webhook'))).toBe(true);
    expect(await (await page.request.get(`/admin/projects/${project.id}/webhooks`)).json()).toHaveLength(0);
  });
});
