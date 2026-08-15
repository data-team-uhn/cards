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
    // Navigating is not the same as being usable. Callers go straight on to click something, and a click
    // that lands before this React application is interactive is silently swallowed.
    await this.expectLoaded();
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
   * Clicks something that reveals something else, and does not believe the first click until the second
   * thing appears.
   *
   * Playwright's actionability checks say a button is visible, stable and hit-testable; none of that means
   * React has bound its handler yet, so an early click is accepted by the DOM and then does nothing. That
   * is what made the narrow-viewport logout intermittently hang for the full test timeout: the drawer
   * button was clicked, the drawer never opened, and the wait for the sign-out item inside it could never
   * be satisfied. Retrying the click is the fix Playwright documents for exactly this; the assertion in
   * between is what makes a swallowed click observable at all.
   */
  private async clickUntilRevealed(trigger: Locator, revealed: Locator) {
    await expect(async () => {
      await trigger.click();
      await expect(revealed).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
  }

  async logout() {
    const size = await this.page.viewportSize();
    if (size && size.width < 900) {
      await this.clickUntilRevealed(this.openDrawerButton, this.navbarLogoutButton);
      await this.navbarLogoutButton.click();
    } else {
      await this.clickUntilRevealed(this.adminAvatarButton, this.logoutButton);
      await this.logoutButton.click();
    }
    // Waiting for the page logging out leads to, rather than for the network to fall quiet: `networkidle`
    // is discouraged precisely because an application that polls never reaches it, and it says nothing
    // about whether the logout took effect.
    await this.page.waitForURL(/\/login/);
  }
}
