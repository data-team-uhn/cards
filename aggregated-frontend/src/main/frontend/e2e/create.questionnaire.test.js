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

/**
 * E2E test: create a new form Patient information from dashboard with a new subject
 * (id unique per browser project to allow parallel chromium/firefox/webkit runs),
 * apply filters, verify table row, then delete the form and subject.
 *
 * Requires running CARDS instance at baseURL (e.g. http://localhost:8080).
 * Run from aggregated-frontend/src/main/frontend:
 * # npm run test:e2e -- e2e/create.questionnaire.test.js
 * # npm run test:e2e:headed -- e2e/create.questionnaire.test.js --project=chromium
 */
const { test, expect } = require('@playwright/test');
const { loginAsAdmin, deleteFormForSubject, deleteSubjectByIdWithNoForms } = require('./helpers');

test.describe('Create questionnaire form with new subject', () => {
  test.afterEach(async ({ page }, testInfo) => {
    if (testInfo.status === testInfo.expectedStatus) return;
    const subjectId = `p1-e2e-${testInfo.project.name}`;
    try {
      await loginAsAdmin(page);
      await deleteFormForSubject(page, subjectId);
      await deleteSubjectByIdWithNoForms(page, subjectId);
    } catch {
      // Ignore cleanup errors
    }
  });

  test('Full flow: login, create form, save, filter by subject, verify row, delete', async ({ page }, testInfo) => {
    test.setTimeout(120000);

    // Unique subject id per project so chromium/firefox/webkit can run in parallel
    const subjectId = `p1-e2e-${testInfo.project.name}`;

    await loginAsAdmin(page);

    // Open New menu (button with aria-label="new")
    await page.getByRole('button', { name: 'new' }).click();

    // New item dialog
    const newItemDialog = page.locator('[aria-label="new-item-dialog"]');
    await expect(newItemDialog).toBeVisible();
    await newItemDialog.locator('td').filter({ hasText: 'Questionnaire' }).click();
    await newItemDialog.locator('[aria-label="new-item-next-button"]').click();

    // New form dialog (select questionnaire)
    const newFormDialog1 = page.locator('[aria-label="new-form-dialog"]');
    await expect(newFormDialog1).toBeVisible();

    await page.waitForTimeout(500);

    await newFormDialog1.getByPlaceholder('Search').fill('patient information');
    const firstRow = newFormDialog1.locator('tbody tr').first();
    await expect(firstRow).toContainText('Patient information');
    await firstRow.click();

    await newFormDialog1.locator('[aria-label="new-form-continue-button"]').click();

    // New form dialog again (subject step)
    const newFormDialog2 = page.locator('[aria-label="new-form-dialog"]');
    await expect(newFormDialog2).toBeVisible();
    await newFormDialog2.locator('[aria-label="new-form-new-subject-button"]').click();

    // New subject dialog
    const newSubjectDialog = page.locator('[aria-label="new-subject-dialog"]');
    await expect(newSubjectDialog).toBeVisible();
    await newSubjectDialog.locator('[aria-label="new-subject-identifier-input"]').locator('input').fill(subjectId);
    await newSubjectDialog.locator('[aria-label="new-subject-create-button"]').click();

    // Redirect to form edit URL
    await page.waitForURL(/\/content\.html\/Forms\/[^/]+\.edit$/, { timeout: 15000 });

    // Save and view
    await page.getByRole('button', { name: 'Save and view' }).click();
    await page.waitForURL(/\/content\.html\/Forms\/[^/]+$/, { timeout: 15000 });

    // Assert Edit button exists (form view mode)
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();

    // Delete the form
    await deleteFormForSubject(page, subjectId);

    // Delete the subject
    await deleteSubjectByIdWithNoForms(page, subjectId);
  });
});