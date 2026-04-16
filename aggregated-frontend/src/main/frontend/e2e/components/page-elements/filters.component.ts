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

export class Filters {
  readonly page: Page;
  readonly addFilterButton: Locator;
  readonly modifyFiltersDialog: Locator;

  constructor(page: Page, view: Locator) {
    this.page = page;
    this.addFilterButton = view.locator('[aria-label="add-filter-button"]');
    // modifyFiltersDialog does not exist in DOM until you click the addFilterButton.
    // page.locator(...) is lazy: it does not immediately query/fail if the element is not in the DOM yet.
    // It creates a locator object that Playwright resolves when you perform an action/assertion
    // (click, fill, expect(...).toBeVisible(), etc.).
    this.modifyFiltersDialog = page.locator('[aria-label="modify-filters-dialog"]');
  }

  async expectVisible() {
    await expect(this.addFilterButton).toBeVisible();
  }

  async filterBySubject(subjectId: string) {
    await this.addFilterButton.click();

    await expect(this.modifyFiltersDialog).toBeVisible({ timeout: 5000 });
    await this.modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');

    // Click the option that shows "Subject" 
    await this.page.getByRole('option', { name: /Subject/ }).first().click();
    await this.modifyFiltersDialog.getByPlaceholder('Search').fill(subjectId);
    await this.page.waitForTimeout(500);
    // Skip disabled placeholder rows (e.g. MUI menuitem with aria-disabled="true").
    const dropdownItem = this.page
      .locator('li[class*="dropdownItem"]:not([aria-disabled="true"])')
      .filter({ visible: true })
      .first();
    if ((await dropdownItem.count()) > 0 && (await dropdownItem.isVisible())) {
      await dropdownItem.click();
    }
    await this.modifyFiltersDialog.locator('[aria-label="apply-filters-button"]').click();
  }

  async verifyFiltersDialogIsClosed() {
    await this.page.waitForLoadState('networkidle');
    await expect(this.modifyFiltersDialog).not.toBeVisible({ timeout: 5000 });
  }
}
