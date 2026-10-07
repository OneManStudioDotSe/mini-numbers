// @ts-check
const { defineConfig } = require('@playwright/test');
const path = require('path');

const JAR = path.resolve(__dirname, '../build/libs/mini-numbers-all.jar');
const DB = path.resolve(__dirname, 'data/e2e.db');

/**
 * Boots the real fat JAR on a throwaway SQLite database for every run.
 * ADMIN_PASSWORD is plain text here on purpose: the app hashes it into the
 * fresh database on first start (see ServiceManager), which is also what a
 * self-hoster gets when they set a plain password in .env.
 */
module.exports = defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:8080',
    channel: 'chrome',
    // The fake tracked site is served by request interception, which Chrome treats as a
    // public-address-space page; its local-network-access check would then block the page
    // from loading the tracker from, or beaconing to, localhost. Real deployments have both
    // ends on public hosts, so the check is irrelevant to what these tests verify.
    launchOptions: { args: ['--disable-features=LocalNetworkAccessChecks,PrivateNetworkAccessChecks'] },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: `rm -f "${DB}" && mkdir -p "${path.dirname(DB)}" && java -jar "${JAR}"`,
    url: 'http://localhost:8080/health',
    timeout: 90_000,
    reuseExistingServer: false,
    env: {
      ADMIN_USERNAME: 'admin',
      ADMIN_PASSWORD: 'E2e-password-123',
      SERVER_SALT: 'e2e-salt-0123456789abcdef0123456789abcdef0123456789abcdef',
      DB_SQLITE_PATH: DB,
      KTOR_DEVELOPMENT: 'true',
      ALLOWED_ORIGINS: '*',
      TRACKER_HEARTBEAT_INTERVAL: '2',
    },
  },
});
