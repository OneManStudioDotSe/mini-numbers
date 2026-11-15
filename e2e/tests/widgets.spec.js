// @ts-check
const { test, expect } = require('@playwright/test');
const { loginViaApi, createProject } = require('../helpers');

test.describe('widgets (lab)', () => {
  test('widgets stay off until the switch in Settings turns them on for the project', async ({ page }, testInfo) => {
    await page.addInitScript(() => localStorage.setItem('mini-numbers-onboarding-dismissed', 'true'));
    await loginViaApi(page.request);
    const project = await createProject(page.request, `widgets ${Date.now()}`);
    const widget = () => page.request.get(`/widget/realtime?key=${project.apiKey}`);

    // The tracking ID is public, so by itself it reads nothing
    expect((await widget()).status()).toBe(404);

    await page.goto('/admin-panel');
    await page.locator('.project-menu__item', { hasText: project.name }).click();
    await page.click('#open-settings-btn');
    const toggle = page.locator('#setting-project-widgets');
    await expect(toggle).toBeEnabled();
    await expect(toggle).not.toBeChecked();

    // The checkbox itself is visually hidden; its slider is what people click
    const slider = page.locator('#setting-project-widgets + .toggle-slider');
    await slider.click();
    await expect(page.locator('.toast').last()).toContainText('Widgets are on for this project');
    expect((await widget()).status()).toBe(200);
    expect(await (await widget()).json()).toHaveProperty('activeVisitors');

    for (const theme of ['light', 'dark']) {
      await page.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
      await page.waitForFunction(() => !document.getAnimations().some(a => a instanceof CSSTransition));
      await page.screenshot({ path: testInfo.outputPath(`widgets-setting-${theme}.png`) });
    }

    // The choice is remembered, and switching off closes the door again at once
    await page.click('#close-settings');
    await page.click('#open-settings-btn');
    await expect(toggle).toBeChecked();
    await slider.click();
    await expect(page.locator('.toast').last()).toContainText('Widgets are off for this project');
    expect((await widget()).status()).toBe(404);
  });
});
