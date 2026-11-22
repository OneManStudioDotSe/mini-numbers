// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('funnels (lab)', () => {
  test('a funnel is created on an empty project, shows its drop-off, is edited and deleted in the dashboard', async ({ page }, testInfo) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    const project = await createProject(page.request, `funnels ${Date.now()}`);
    // One visit goes home -> pricing -> signup, one stops at pricing, one only sees home, one never enters
    const journeys = {
      'fun-d': [{ path: '/about', type: 'pageview' }],
      'fun-a': [{ path: '/', type: 'pageview' }, { path: '/pricing', type: 'pageview' }, { path: '/pricing', type: 'custom', eventName: 'signup' }],
      'fun-b': [{ path: '/', type: 'pageview' }, { path: '/pricing', type: 'pageview' }],
      'fun-c': [{ path: '/', type: 'pageview' }],
    };
    for (const [sessionId, events] of Object.entries(journeys)) {
      for (const event of events) {
        const r = await page.request.post(`/collect?key=${project.apiKey}`, {
          headers: { 'Content-Type': 'text/plain' },
          data: JSON.stringify({ ...event, sessionId }),
        });
        expect(r.status(), `${sessionId} ${event.type} ${event.path}`).toBe(202);
      }
    }

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.locator('[data-section="conversions"] .dashboard-section__header').click();

    const card = page.locator('#funnels-section');
    await expect(card).toContainText('No funnels configured yet');
    await page.click('#manage-funnels-btn');
    const modal = page.locator('#funnels-modal');
    await expect(modal).toHaveClass(/show/);

    // Create: Home -> Pricing. A refused save keeps the dialog open and says why
    const steps = modal.locator('.funnel-step-input');
    await expect(steps).toHaveCount(2);
    await expect(steps.nth(0).locator('[data-action="remove-funnel-step"]'), 'a funnel keeps two steps').toBeHidden();
    await page.fill('#funnel-name', 'Signup flow');
    await modal.locator('#create-funnel-btn').click();
    await expect(page.locator('.toast').last()).toContainText('Every step needs a name and a match value');
    await expect(modal).toHaveClass(/show/);
    await steps.nth(0).locator('[data-step="name"]').fill('Home');
    await steps.nth(0).locator('[data-step="match"]').fill('/');
    await steps.nth(1).locator('[data-step="name"]').fill('Pricing');
    await steps.nth(1).locator('[data-step="match"]').fill('/pricing');

    // Removing a step in the middle renumbers the rest
    await page.click('#add-funnel-step-btn');
    await page.click('#add-funnel-step-btn');
    await steps.nth(3).locator('[data-step="name"]').fill('Last');
    await steps.nth(2).locator('[data-action="remove-funnel-step"]').click();
    await expect(steps).toHaveCount(3);
    await expect(steps.nth(2).locator('.funnel-step-input__number')).toHaveText('Step 3');
    await expect(steps.nth(2).locator('[data-step="name"]')).toHaveValue('Last');
    await steps.nth(2).locator('[data-action="remove-funnel-step"]').click();
    await modal.locator('#create-funnel-btn').click();

    const tableRow = page.locator('#funnels-table-body tr', { hasText: 'Signup flow' });
    await expect(tableRow).toContainText('Home → Pricing');
    await expect(page.locator('#funnel-name')).toHaveValue('');
    expect(await (await page.request.get(`/admin/projects/${project.id}/funnels`)).json()).toHaveLength(1);

    // The dashboard card shows how many visits reach each step
    await page.click('#close-funnels-modal');
    const funnelSteps = card.locator('.funnel-step');
    await expect(funnelSteps).toHaveCount(2);
    await expect(funnelSteps.nth(0)).toContainText('3 sessions');
    await expect(funnelSteps.nth(1)).toContainText('2 sessions');
    await expect(funnelSteps.nth(0)).toContainText('33.3% drop-off');
    // Bars are a share of the visits that entered the funnel; the visit that only saw /about is not among them
    await expect(funnelSteps.nth(0).locator('.funnel-step__percent')).toHaveText('100.0%');
    await expect(funnelSteps.nth(1).locator('.funnel-step__percent')).toHaveText('66.7%');

    // Edit: the form is filled from the funnel; add a third step and rename
    await page.click('#manage-funnels-btn');
    await tableRow.locator('[data-action="edit-funnel"]').click();
    await expect(page.locator('#funnel-form-title')).toHaveText('Edit funnel');
    await expect(page.locator('#cancel-funnel-edit-btn')).toBeVisible();
    await expect(page.locator('#funnel-name')).toHaveValue('Signup flow');
    await expect(steps).toHaveCount(2);
    await expect(steps.nth(1).locator('[data-step="match"]')).toHaveValue('/pricing');
    await page.click('#add-funnel-step-btn');
    await steps.nth(2).locator('[data-step="name"]').fill('Signed up');
    await steps.nth(2).locator('[data-step="type"]').selectOption('event');
    await steps.nth(2).locator('[data-step="match"]').fill('signup');
    await page.fill('#funnel-name', 'Signup flow v2');

    for (const theme of ['light', 'dark']) {
      await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await page.screenshot({ path: testInfo.outputPath(`funnels-modal-${theme}.png`) });
    }

    await modal.locator('#create-funnel-btn').click();
    const edited = page.locator('#funnels-table-body tr', { hasText: 'Signup flow v2' });
    await expect(edited).toContainText('Home → Pricing → Signed up');
    await expect(page.locator('#funnels-table-body tr')).toHaveCount(1);
    await expect(page.locator('#funnel-form-title')).toHaveText('Create new funnel');
    await expect(page.locator('#cancel-funnel-edit-btn')).toBeHidden();

    await page.click('#close-funnels-modal');
    await expect(card).toContainText('Signup flow v2');
    await expect(funnelSteps).toHaveCount(3);
    await expect(funnelSteps.nth(2)).toContainText(/\b1 session$/m);
    await expect(funnelSteps.nth(1), 'half of those on pricing sign up').toContainText('50.0% drop-off');
    for (const theme of ['light', 'dark']) {
      await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await card.screenshot({ path: testInfo.outputPath(`funnels-card-${theme}.png`) });
    }

    // Delete
    await page.click('#manage-funnels-btn');
    page.once('dialog', d => d.accept());
    await edited.locator('[data-action="delete-funnel"]').click();
    await expect(page.locator('#funnels-table-body')).toContainText('No funnels yet');
    await page.click('#close-funnels-modal');
    await expect(card).toContainText('No funnels configured yet');
  });
});
