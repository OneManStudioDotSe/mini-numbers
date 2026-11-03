// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaUi, loginViaApi, createProject } = require('../helpers');

// Breaks out of a double-quoted attribute and runs script wherever it is inserted unescaped
const CANARY = `"><img src=x onerror="window.__xss=1"> Bob's "shop" <b>`;

test.describe('escaping (audit H2)', () => {
  test('admin-entered names render as text and the row actions still work', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaUi(page);
    const request = page.request; // same cookie jar as the logged-in page

    const created = await (await request.post('/admin/projects', { data: { name: CANARY, domain: 'xss.example.com' } })).json();
    const goal = await request.post(`/admin/projects/${created.id}/goals`, { data: { name: CANARY, goalType: 'url', matchValue: '/thanks' } });
    expect(goal.status(), 'create goal').toBe(201);
    const segment = await request.post(`/admin/projects/${created.id}/segments`, {
      data: { name: CANARY, filters: [{ field: 'browser', operator: 'equals', value: CANARY, logic: 'AND' }] },
    });
    expect(segment.status(), 'create segment').toBe(201);

    // Utils.escapeHtml escapes both quote kinds, so it is safe inside attributes too
    await page.reload();
    expect(await page.evaluate(() => Utils.escapeHtml(`<a href="x">Bob's & co</a>`)))
      .toBe('&lt;a href=&quot;x&quot;&gt;Bob&#39;s &amp; co&lt;/a&gt;');

    // Sidebar: the name is text; clicking the item selects it; the delete button names it in the dialog
    const item = page.locator('.project-menu__item', { hasText: "Bob's" });
    await expect(item.locator('.project-menu__name')).toHaveText(CANARY);
    await item.click();
    await expect.poll(() => page.evaluate(() => Dashboard.state.currentProjectId)).toBe(created.id);
    await item.locator('.project-menu__delete').click();
    await expect(page.locator('#delete-project-modal')).toHaveClass(/show/);
    await expect(page.locator('#delete-project-name')).toHaveText(CANARY);
    await page.locator('#delete-project-modal').getByRole('button', { name: 'Cancel' }).click();

    // Goals dialog: the row is text; the delete button passes the real name to the confirm prompt
    const conversions = page.locator('.dashboard-section__header[aria-controls="section-conversions-content"]');
    if (await conversions.getAttribute('aria-expanded') === 'false') await conversions.click();
    await page.click('#manage-goals-btn');
    await expect(page.locator('#goals-table-body')).toContainText(CANARY);
    const confirmText = new Promise(resolve => page.once('dialog', d => { resolve(d.message()); d.dismiss(); }));
    await page.locator('#goals-table-body [data-action="delete-goal"]').click();
    expect(await confirmText).toContain(CANARY);
    await page.click('#close-goals-modal');

    // Segment card: name and filter value are text; Analyze shows the name in the toast as text
    await expect(page.locator('#segments-list')).toContainText(CANARY);
    await page.locator('#segments-list [data-action="analyze-segment"]').click();
    await expect(page.locator('.toast--success')).toContainText(CANARY);

    // Nothing was injected anywhere on the page
    expect(await page.locator('img[src="x"]').count(), 'injected img').toBe(0);
    expect(await page.evaluate(() => window.__xss), 'onerror ran').toBeUndefined();
  });

  test('a referrer that carries markup is refused at /collect', async ({ request }) => {
    await loginViaApi(request);
    const project = await createProject(request);
    const post = referrer => request.post('/collect', {
      headers: { 'X-Project-Key': project.apiKey },
      data: { path: '/home', referrer, sessionId: 'xss-canary', type: 'pageview' },
    });
    expect((await post('https://evil.test/" onmouseover="alert(1)')).status()).toBe(400);
    expect((await post('https://evil.test/<script>')).status()).toBe(400);
    expect((await post("https://en.wikipedia.org/wiki/Don't_Look_Up")).status()).toBe(202);
  });
});
