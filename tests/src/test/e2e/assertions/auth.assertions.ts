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

import { expect, Page } from '@playwright/test';

import { HomePage } from '../pages/home.page';
import { LoginPage } from '../pages/login.page';

export class AuthAssertions {
  constructor(private page: Page) {}
  /**
   * Assert user is successfully logged in i.e. land on a "home page"
   */
  async expectLoggedIn() {
    const homePage = new HomePage(this.page);
    await homePage.expectLoaded();
  }

  /**
   * Assert login failed due to invalid credentials
   */
  async expectInvalidCredentials() {
    const errorAlert = this.page.getByRole('alert');

    await expect(errorAlert).toBeVisible();
    await expect(errorAlert).toContainText('Invalid username or password');
  }

  /**
   * Assert user is redirected to login page
   */
  async expectOnLoginPage() {
    const loginPage = new LoginPage(this.page);
    await loginPage.expectLoaded();
  }

  /**
   * Assert specific validation error (e.g. empty fields)
   */
  async expectValidationError(message: string) {
    await expect(this.page.getByText(message)).toBeVisible();
  }

  /**
   * Assert user is logged out
   */
  async expectLoggedOut() {
    const logoutSessionResponse = await this.page.request.get('/system/sling/info.sessionInfo.json');
    expect(logoutSessionResponse.ok()).toBeTruthy();
    const logoutSessionData = await logoutSessionResponse.json();
    expect(logoutSessionData.userID).toBe('anonymous');
    await this.expectOnLoginPage();
  }
}
