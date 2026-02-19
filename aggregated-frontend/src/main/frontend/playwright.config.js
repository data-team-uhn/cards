/**
 * Playwright config for aggregated-frontend e2e tests.
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
    launchOptions: {
      slowMo: 500,
    },
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
    command: "bash ./start_cards.sh",
    // The script itself uses this endpoint to determine readiness
    url: "http://localhost:8080/system/sling/info.sessionInfo.json",
    timeout: 180000,
    reuseExistingServer: !process.env.CI,
  },
});
