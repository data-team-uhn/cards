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

import { AuthAssertions } from '../../assertions/auth.assertions';
import { TEST_DATA_ONLY_PATHS, adminHeaders } from '../../config/test-data';
import { users, type User } from '../../config/users';
import { loginAs } from '../../flows/auth/login.flow';

/**
 * The distribution with the test content installed, where every feature has something to show. This is
 * where broad functional coverage belongs; the tests below only establish that the instance really is
 * carrying the test data and the modules that go with it, which is what distinguishes it from `core`.
 *
 * Keep that distinction asserted. Two suites silently pointed at the same instance is the failure mode
 * these tests exist to catch, and nothing else here would notice it.
 *
 * Requests are addressed directly at the content nodes rather than listing their parent: the homepage
 * nodes render themselves, not their children, so `/Questionnaires.1.json` returns two properties and no
 * questionnaires at all — which would make a "the content is here" assertion quietly untestable.
 */
test.describe('the instance with test data', () => {
  for (const { path, description } of TEST_DATA_ONLY_PATHS) {
    test(`carries ${description}`, async ({ request }) => {
      const response = await request.get(path, { headers: adminHeaders() });

      expect(response.ok()).toBeTruthy();
    });
  }

  test('signs in and leaves the sign-in page, as on the bare distribution', async ({ page }) => {
    // The extra features register UI extensions of their own, so the dashboard this lands on is not the
    // one the `core` suite sees; that signing in still works is worth its own assertion.
    await loginAs(page, users.admin as User);

    await new AuthAssertions(page).expectLoggedIn();
  });
});
