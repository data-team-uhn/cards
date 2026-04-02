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
import { Filters } from './filters.component';

export class FormsView {
  readonly page: Page;
  readonly filters: Filters;
  readonly formsView: Locator;

  constructor(page: Page) {
    this.page = page;
    this.formsView = page.locator('[aria-label="forms-view"]');
    this.filters = new Filters(page, this.formsView);
  }

  async expectVisible() {
    await expect(this.formsView).toBeVisible();
  }

  async filterBySubject(subjectId: string) {
    await this.filters.filterBySubject(subjectId);
    await this.filters.verifyFiltersDialogIsClosed();
  }

  /**
   * Delete the form by subject id.
   * Assumes that the form is the only one for the subject.
   * @param {string} subjectId
   */
  async deleteFormBySubject(subjectId: string) {
    await this.filterBySubject(subjectId);

    // Verify table has exactly one data row
    const dataRows = this.formsView.locator('tbody tr');
    await expect(dataRows).toHaveCount(1);

    // Check that the row contains the subject id
    const row = dataRows.first();
    await expect(row.locator('td').first().locator('a')).toHaveText(new RegExp(`^${subjectId} :`));

    await row.locator('td').last().locator('[aria-label="Delete form"]').click();

    // Delete confirmation dialog
    const deleteDialog = this.page.locator('[aria-label="delete-dialog"]');
    await expect(deleteDialog).toBeVisible({ timeout: 5000 });
    await deleteDialog.locator('[aria-label="delete-button"]').click();
    await this.page.waitForLoadState('networkidle');

    // Form link should no longer exist
    await expect(this.formsView.locator('a').filter({ hasText: `${subjectId} :` })).toHaveCount(0);
  }

  async editFormBySubject(subjectId: string) {
    await this.filterBySubject(subjectId);

    // Verify table has exactly one data row
    const dataRows = this.formsView.locator('tbody tr');
    await expect(dataRows).toHaveCount(1);

    // Check that the row contains the subject id
    const row = dataRows.first();
    await expect(row.locator('td').first().locator('a')).toHaveText(new RegExp(`^${subjectId} :`));

    await row.locator('td').last().locator('a[aria-label="Edit form"]').click();
    await this.page.waitForLoadState('networkidle');

    await this.page.waitForURL(/\/content\.html\/Forms\/[^/]+\.edit$/, { timeout: 15000 });
  }
}
