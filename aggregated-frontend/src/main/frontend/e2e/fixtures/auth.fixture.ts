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

import { test as base } from '@playwright/test';
import { HomePage } from '../pages/home.page';
import { users } from '../config/users';
import { loginAs } from '../flows/auth/login.flow';
import { AuthAssertions } from '../assertions/auth.assertions';

type AuthFixtures = {
  adminHomePage: HomePage;
};

export const test = base.extend<AuthFixtures>({
  adminHomePage: async ({ browser }, use) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    // perform login - flow used here
    await loginAs(page, users.admin);

    // create HomePage object
    const homePage = new HomePage(page);

    // ensure login succeeded
    const authAssertions = new AuthAssertions(page);
    await authAssertions.expectLoggedIn();

    // provide ready-to-use page
    await use(homePage);

    // cleanup: close browser context
    await context.close();
  },
});

export { expect } from '@playwright/test';
