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

const { expect } = require('@playwright/test');

/**
 * Log in as admin (username/password admin/admin) and wait for dashboard.
 * @param {import('@playwright/test').Page} page
 */
async function loginAsAdmin(page) {
  await page.goto('/');
  await page.waitForLoadState('networkidle');

  await page.locator('input[name="j_username"], input#j_username').fill('admin');
  await page.locator('input[name="j_password"], input#j_password').fill('admin');

  await page.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Login")').click();

  await page.waitForURL(/\/content\.html\/Questionnaires\/User|\/$/, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  await expect(page).toHaveTitle(/Dashboard | Your Experience/i);
}

/**
 * Click the first visible element in the locator set.
 * @param {import('@playwright/test').Locator} locator
 */
async function clickFirstVisible(locator) {
  const first = locator.filter({ visible: true }).first();
  await first.click();
}

/**
 * Sign out: open user menu (avatar or drawer) then click sign out; assert anonymous session.
 * @param {import('@playwright/test').Page} page
 * @param {{ mode: 'wide' | 'narrow' }} options - 'wide': use avatar button; 'narrow': use open drawer button
 */
async function logout(page, { mode }) {
  if (mode === 'wide') {
    await clickFirstVisible(page.getByTestId('admin-avatar'));
  } else if (mode === 'narrow') {
    await page.locator('[aria-label="open drawer"]').click();
  } else {
    throw new Error(`Unknown logout mode: ${mode}`);
  }

  await clickFirstVisible(page.getByTestId('admin-signout'));

  await page.waitForURL(/\/login|\/$/, { timeout: 10000 });
  await page.waitForLoadState('networkidle');

  const logoutSessionResponse = await page.request.get('/system/sling/info.sessionInfo.json');
  expect(logoutSessionResponse.ok()).toBeTruthy();
  const logoutSessionData = await logoutSessionResponse.json();
  expect(logoutSessionData.userID).toBe('anonymous');
}

module.exports = {
  loginAsAdmin,
  clickFirstVisible,
  logout,
};
