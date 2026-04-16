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

import { expect, Locator, Page } from '@playwright/test';

export class Dashboard {
  readonly page: Page;
  readonly adminAvatarButton: Locator;
  readonly openDrawerButton: Locator;
  readonly logoutButton: Locator;
  readonly navbarLogoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.adminAvatarButton = this.page.getByRole('button', { name: 'admin-avatar' });
    this.openDrawerButton = this.page.locator('[aria-label="open drawer"]');
    this.logoutButton = this.page.getByRole("tooltip").getByLabel('admin-signout');
    this.navbarLogoutButton = this.page.getByLabel('sidebar-wrapper').getByLabel('admin-signout');
  }

  async goto() {
    await this.page.goto('/content.html/Questionnaires/User');
  }

  async expectLoaded() {
    const size = await this.page.viewportSize();
    if (size && size.width < 900) {
      await expect(this.openDrawerButton).toBeVisible();
    } else {
      await expect(this.adminAvatarButton).toBeVisible();
    }
  }

  /**
   * Sign out: open user menu (avatar or drawer) then click sign out; assert anonymous session.
   * @param {import('@playwright/test').Page} page
   */
  async logout() {
    const size = await this.page.viewportSize();
    if (size && size.width < 900) {
      await this.openDrawerButton.click();
      await this.navbarLogoutButton.click();
    } else {
      await this.adminAvatarButton.click();
      await this.logoutButton.click();
    }
    await this.page.waitForLoadState('networkidle');
  }
}
