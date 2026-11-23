// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('email reports', () => {
  test('a report is created and deleted in the dialog', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    const project = await createProject(page.request, `email reports ${Date.now()}`);

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.click('#open-settings-btn');
    await page.click('#manage-email-reports-btn');
    const modal = page.locator('#email-reports-modal');
    await expect(modal).toHaveClass(/show/);
    await expect(modal.locator('#smtp-status-indicator')).toContainText('Connected');
    await expect(modal.locator('#email-reports-table-body')).toContainText('No email reports yet');

    // Create: a daily report to one address
    await page.fill('#email-report-recipient', 'owner@example.com');
    await page.selectOption('#email-report-schedule', 'DAILY');
    await page.click('#create-email-report-btn');
    const row = modal.locator('#email-reports-table-body tr', { hasText: 'owner@example.com' });
    await expect(row).toBeVisible();
    await expect(row).toContainText('DAILY');
    await expect(row).toContainText('Never');
    await expect(page.locator('#email-report-recipient')).toHaveValue('');
    const listed = await (await page.request.get(`/admin/projects/${project.id}/email-reports`)).json();
    expect(listed).toHaveLength(1);

    // Delete (the dialog asks first)
    page.once('dialog', d => d.accept());
    await row.locator('button[title="Delete"]').click();
    await expect(modal.locator('#email-reports-table-body')).toContainText('No email reports yet');
    expect(await (await page.request.get(`/admin/projects/${project.id}/email-reports`)).json()).toHaveLength(0);
  });
});
