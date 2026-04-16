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

export class CreateNewItem {
  readonly page: Page;
  readonly newItemButton: Locator;
  readonly newItemDialog: Locator;
  readonly newFormDialog: Locator;
  readonly newSubjectDialog: Locator;
  readonly selectParentDialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.newItemButton = page.getByRole('button', { name: 'new' });
    this.newItemDialog = page.locator('[aria-label="new-item-dialog"]');
    this.newFormDialog = page.locator('[aria-label="new-form-dialog"]');
    this.newSubjectDialog = page.locator('[aria-label="new-subject-dialog"]');
    this.selectParentDialog = page.locator('[aria-label="select-parent-dialog"]');
  }

  async expectVisible() {
    await expect(this.newItemButton).toBeVisible();
  }

  /**
   * New → Questionnaire → search/select questionnaire → continue → new subject → create with identifier.
   * Note: we using the default required subject selection in the dialog, so we don't need to select it.
   * @param {string} questionnaireName - name of the questionnaire to create
   * @param {string} subjectId - id of the new subject
   */
  async createFormWithNewSubject(questionnaireName: string, subjectId: string) {
    await this.newItemButton.click();

    await expect(this.newItemDialog).toBeVisible();
    await this.newItemDialog.locator('td').filter({ hasText: 'Questionnaire' }).click();
    await this.newItemDialog.locator('[aria-label="new-item-next-button"]').click();

    await expect(this.newFormDialog).toBeVisible();

    await this.page.waitForLoadState('networkidle');

    await this.newFormDialog.getByRole('textbox', { name: 'Search' }).fill(questionnaireName);
    const firstRow = this.newFormDialog.locator('tbody tr').first();
    await expect(firstRow).toContainText(questionnaireName);
    await firstRow.click();

    await this.newFormDialog.locator('[aria-label="new-form-continue-button"]').click();
    await this.newFormDialog.locator('[aria-label="new-form-new-subject-button"]').click();

    await this.newSubjectDialog.locator('[aria-label="new-subject-identifier-input"]').locator('input').fill(subjectId);
    await this.newSubjectDialog.getByRole('button', { name: 'new-subject-create-button' }).click();

    await this.page.waitForLoadState('networkidle');

    // check if we accidentally bumped into situation of duplucate subjects and report the relevant error
    if (await this.newSubjectDialog.isVisible()) {
      if (await this.newSubjectDialog.getByRole('alert').isVisible()) {
        throw new Error('Patient' + subjectId + ' already exists');
      }
    }
  }

  /**
   * Select Patient parent subject (search + row selection in select-parent dialog) and continue.
   * @param {string} subjectId - parent Patient subject id
   */
  async selectPatientSubjectParentByName(subjectId: string) {
    await this.selectParentDialog.getByRole('textbox', { name: 'Search' }).fill(subjectId);
    // Global filter in MaterialReactTable is async; hierarchy text includes the identifier but may not match a single cell accessible name.
    const parentRows = this.selectParentDialog.getByRole('row').filter({ hasText: subjectId });
    await expect(parentRows.first()).toBeVisible({ timeout: 60000 });

    const count = await parentRows.count();
    let firstEnabledRow: Locator | null = null;
    for (let i = 0; i < count; i++) {
      const row = parentRows.nth(i);
      if (await row.isEnabled()) {
        firstEnabledRow = row;
        break;
      }
    }
    if (!firstEnabledRow) {
      throw new Error(`No enabled parent row found for subject: ${subjectId}`);
    }
    await firstEnabledRow.click();

    await this.selectParentDialog.getByRole('button', { name: 'Continue' }).click();
    await this.page.waitForLoadState('networkidle');

    await this.verifyNewItemDialogIsClosed();
  }

  async createParentSubjectForGivenSubject(subjectId: string) {
    await this.selectParentDialog.getByRole('button', { name: 'New subject' }).click();
    await this.newSubjectDialog.getByRole('textbox', { name: 'Enter subject identifier' }).fill(subjectId);
    await this.newSubjectDialog.getByRole('button', { name: 'new-subject-create-button' }).click();

    await this.page.waitForLoadState('networkidle');

    // check if we accidentally bumped into situation of duplucate subjects and report the relevant error
    if (await this.selectParentDialog.isVisible()) {
      if (await this.selectParentDialog.getByRole('alert').isVisible()) {
        throw new Error('Patient' + subjectId + ' already exists');
      }
    }
  }

  async verifyNewItemDialogIsClosed() {
    await this.page.waitForLoadState('networkidle');
    await expect(this.newItemDialog).not.toBeVisible();
    await expect(this.newFormDialog).not.toBeVisible();
    await expect(this.newSubjectDialog).not.toBeVisible();
    await expect(this.selectParentDialog).not.toBeVisible();
  }
}
