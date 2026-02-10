/**
 * Playwright config for aggregated-frontend e2e tests.
 * Run against a running CARDS instance (e.g. start_cards2.sh or mvn + Sling).
 *
 * Set CARDS_URL to target a different instance, e.g.:
 *   CARDS_URL=http://localhost:9090 yarn test:e2e
 */
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'dot' : [['html', { open: 'never' }]],
  use: {
    baseURL: process.env.CARDS_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  timeout: 30000,
  expect: {
    timeout: 10000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    // Start your app via the script you use today :contentReference[oaicite:2]{index=2}
    command: "bash ./start_cards2.sh -p 8080",
    // The script itself uses this endpoint to determine readiness :contentReference[oaicite:3]{index=3}
    url: "http://localhost:8080/system/sling/info.sessionInfo.json",
    timeout: 180000,
    reuseExistingServer: !process.env.CI,
  },
});
