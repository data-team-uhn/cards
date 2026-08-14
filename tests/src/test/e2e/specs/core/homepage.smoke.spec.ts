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

/**
 * The application name a stock instance carries, which is what every page title is built from. It is the
 * `app.name` property in the root POM, and a project distribution is expected to override it -- so this
 * asserts what the core distribution these tests launch actually ships.
 */
const APP_NAME = 'Clinical ARchive for Data Science';

/**
 * The homepage is reachable, under each of the three paths that serve it, and is what CARDS serves rather
 * than a Sling default.
 *
 * At the API level rather than through the browser: an unauthenticated request is exactly what a person
 * arriving at the site makes, these run where no browser can, and the three paths differ only in how
 * Sling resolves them, which a rendered page would not tell apart.
 */
test.describe('the homepage', () => {
  for (const location of ['/', '/content', '/content.html']) {
    test(`is served as HTML at ${location}`, async ({ request }) => {
      const response = await request.get(location);

      expect(response.ok()).toBeTruthy();
      expect(response.headers()['content-type']).toMatch(/^text\/html/);
      expect(await response.text()).toContain(`<title>${APP_NAME}</title>`);
    });
  }
});

test('the browser is shown the homepage at the context root', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle(APP_NAME);
});
