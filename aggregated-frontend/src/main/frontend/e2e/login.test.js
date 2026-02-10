/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Login / logout test.
 *
 * To see tests in browser, in /cards/aggregated-frontend/src/main/frontend folder run from terminal:
 * # npm run test:e2e:headed -- --project=chromium --workers=1
 * - requires running local CARDS instance at http://localhost:8080
 * - requires playwright installed: # npx playwright install
 */
const { test, expect } = require('@playwright/test');
const { loginAsAdmin, logout } = require('./helpers');

test.describe('Login and Logout test', () => {

  test('Page  loads', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle("Your Experience");
  });

  test('Session info endpoint is reachable', async ({ request }) => {
    const response = await request.get('/system/sling/info.sessionInfo.json');
    expect(response.ok()).toBeTruthy();
  });

  test("Login as admin and logout with wide viewport", async ({ page }) => {
    await loginAsAdmin(page);
    await logout(page, { mode: "wide" });
  });

  test("Login as admin and logout with narrow viewport", async ({ page }) => {
    await page.setViewportSize({ width: 800, height: 600 });
    await loginAsAdmin(page);
    await logout(page, { mode: "narrow" });
  });
});
