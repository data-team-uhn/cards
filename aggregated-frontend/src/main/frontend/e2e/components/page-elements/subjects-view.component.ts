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

export class SubjectsView {
  readonly page: Page;
  readonly filters: Filters;
  readonly subjectsView: Locator;

  constructor(page: Page) {
    this.page = page;
    this.subjectsView = page.locator('[aria-label="subjects-view"]');
    this.filters = new Filters(page, this.subjectsView);
  }

  async expectVisible() {
    await expect(this.subjectsView).toBeVisible();
  }

  async filterBySubject(subjectId: string) {
    await this.filters.filterBySubject(subjectId);
    await this.filters.verifyFiltersDialogIsClosed();
  }

  /**
   * Delete the subject with the given id from the subjects view.
   * @param {string} subjectId
   * @param {string} type - 'visit' or 'patient'
   * @param {boolean} [hasForms] second delete-dialog confirm when subject has linked forms
   */
  async deleteSubjectById(subjectId: string, type: 'visit' | 'patient', hasForms: boolean = false) {
    if (type === 'visit') {
      await this.subjectsView.getByRole('tab', { name: 'Visits' }).click();
    }

    await this.filterBySubject(subjectId);
    await this.page.waitForLoadState('networkidle');

    const row = this.subjectsView.locator('tbody tr').first();
    await expect(row.locator('td').first().locator('a')).toContainText(subjectId);
    await expect(row.locator('td').nth(2)).toContainText('admin');

    await row.locator('td').last().locator('[aria-label="Delete subject"]').click();

    const deleteDialog = this.page.locator('[aria-label="delete-dialog"]');
    await expect(deleteDialog).toBeVisible({ timeout: 5000 });
    await deleteDialog.locator('[aria-label="delete-button"]').click();

    if (hasForms) {
      const deleteDialog2 = this.page.locator('[aria-label="delete-dialog"]');
      await deleteDialog2.getByRole('button', { name: 'delete-button' }).click();
    }

    await this.page.waitForLoadState('networkidle');
  }
  
  /**
   * Delete subject when it has no forms.
   * @param {string} subjectId
   * @param {string} type - 'visit' or 'patient'
   */
  async deleteSubjectByIdWithNoForms(subjectId: string, type: 'visit' | 'patient') {
    return this.deleteSubjectById(subjectId, type, false);
  }

  /**
   * Delete subject when it has forms.
   * @param {string} subjectId
   * @param {string} type - 'visit' or 'patient'
   */
  async deleteSubjectByIdWithForms(subjectId: string, type: 'visit' | 'patient') {
    return this.deleteSubjectById(subjectId, type, true);
  }
}
