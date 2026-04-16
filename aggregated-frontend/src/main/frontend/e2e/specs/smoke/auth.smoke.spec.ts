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

import { test, expect } from '@playwright/test';
import { loginAs } from '../../flows/auth/login.flow';
import { logout } from '../../flows/auth/logout.flow';
import { AuthAssertions } from '../../assertions/auth.assertions';
import { users, type User } from '../../config/users';

test('Page loads', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle("Your Experience");
});

test('Session info endpoint is reachable', async ({ request }) => {
  const response = await request.get('/system/sling/info.sessionInfo.json');
  expect(response.ok()).toBeTruthy();
});

test('user can log in and logout with wide viewport as admin', { tag: '@smoke' }, async ({ page }) => {
  await loginAs(page, users.admin as User);

  const authAssertions = new AuthAssertions(page);
  await authAssertions.expectLoggedIn();

  await logout(page);
  await authAssertions.expectLoggedOut();
});

test.describe('narrow layout', () => {
  test.use({ viewport: { width: 800, height: 600 } });
  test("user can log in and logout with narrow viewport as admin", async ({ page }) => {
    await loginAs(page, users.admin as User);

    const authAssertions = new AuthAssertions(page);
    await authAssertions.expectLoggedIn();

    await logout(page);
    await authAssertions.expectLoggedOut();
  });
});

test('user cannot login with wrong login/password', async ({ page }) => {
  const authAssertions = new AuthAssertions(page);

  await loginAs(page, users.nonexistentUser as User);
  await authAssertions.expectInvalidCredentials();
});
