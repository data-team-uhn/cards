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
 * E2E test: create a new form Patient information from dashboard with new subject p1-e2e,
 * apply filters, verify table row, then delete the form.
 *
 * Requires running CARDS instance at baseURL (e.g. http://localhost:8080).
 * Run from aggregated-frontend/src/main/frontend:
 * # npm run test:e2e:headed -- e2e/create.questionnaire.test.js --project=chromium --workers=1
 */
const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Create questionnaire form with new subject p1-e2e', () => {
  test('Full flow: login, create form, save, filter by subject, verify row, delete', async ({ page }) => {
    test.setTimeout(120000);

    await loginAsAdmin(page);

    // Open New menu (button with aria-label="new")
    await page.getByRole('button', { name: 'new' }).click();

    // New item dialog
    const newItemDialog = page.getByTestId('new-item-dialog');
    await expect(newItemDialog).toBeVisible();
    await newItemDialog.locator('td').filter({ hasText: 'Questionnaire' }).click();
    await newItemDialog.getByTestId('new-item-next-button').click();

    // New form dialog (select questionnaire)
    const newFormDialog1 = page.getByTestId('new-form-dialog');
    await expect(newFormDialog1).toBeVisible();

    await page.waitForTimeout(500);

    await newFormDialog1.getByPlaceholder('Search').fill('patient information');
    const firstRow = newFormDialog1.locator('tbody tr').first();
    await expect(firstRow).toContainText('Patient information');
    await firstRow.click();

    await newFormDialog1.getByTestId('new-form-continue-button').click();

    // New form dialog again (subject step)
    const newFormDialog2 = page.getByTestId('new-form-dialog');
    await expect(newFormDialog2).toBeVisible();
    await newFormDialog2.getByTestId('new-form-new-subject-button').click();

    // New subject dialog
    const newSubjectDialog = page.getByTestId('new-subject-dialog');
    await expect(newSubjectDialog).toBeVisible();
    await newSubjectDialog.getByTestId('new-subject-identifier-input').locator('input').fill('p1-e2e');
    await newSubjectDialog.getByTestId('new-subject-create-button').click();

    // Redirect to form edit URL
    await page.waitForURL(/\/content\.html\/Forms\/[^/]+\.edit$/, { timeout: 15000 });

    // Save and view
    await page.getByRole('button', { name: 'Save and view' }).click();
    await page.waitForURL(/\/content\.html\/Forms\/[^/]+$/, { timeout: 15000 });

    // Assert Edit button exists (form view mode)
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();

    // Go to Questionnaires/User dashboard
    await page.goto('/content.html/Questionnaires/User');
    await page.waitForLoadState('networkidle');

    // Open filters: click add-filter-button
    const formsView1 = page.getByTestId('forms-view');
    await expect(formsView1).toBeVisible();
    await formsView1.getByTestId('add-filter-button').click();

    // Modify filters dialog
    const modifyFiltersDialog = page.getByTestId('modify-filters-dialog');
    await expect(modifyFiltersDialog).toBeVisible();
    await modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');
    // Click the option that shows "Subject" (variable name; MUI Autocomplete uses role="option")
    await page.getByRole('option', { name: /Subject/ }).first().click();

    await modifyFiltersDialog.getByPlaceholder('Search').fill('p1-e2e');
    await page.waitForTimeout(500);
    await page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first().click();

    await modifyFiltersDialog.getByTestId('apply-filters-button').click();

    // Dialog should close
    await expect(modifyFiltersDialog).not.toBeVisible();

    await page.waitForLoadState('networkidle');

    // Table should have exactly one data row
    const formsView2 = page.getByTestId('forms-view');
    const dataRows = formsView2.locator('tbody tr');
    await expect(dataRows).toHaveCount(1);

    const row = dataRows.first();
    // First td contains link with "p1-e2e : Patient information"
    await expect(row.locator('td').first().locator('a')).toContainText('p1-e2e : Patient information');
    // Column that contains "admin" (Created by column)
    await expect(row.locator('td').nth(2)).toContainText('admin');
    // Last td: Actions column
    const lastTd = row.locator('td').last();
    await expect(lastTd.locator('a[aria-label="Edit form"]')).toBeVisible();
    const deleteFormButton = lastTd.locator('[aria-label="Delete form"]');
    await expect(deleteFormButton).toBeVisible();

    await deleteFormButton.click();

    // Delete confirmation dialog
    const deleteDialog = page.getByTestId('delete-dialog');
    await expect(deleteDialog).toBeVisible();
    await deleteDialog.getByTestId('delete-button').click();

    await page.waitForLoadState('networkidle');

    // Form link should no longer exist
    const formsView3 = page.getByTestId('forms-view');
    await expect(formsView3.locator('a').filter({ hasText: 'p1-e2e : Patient information' })).toHaveCount(0);

    // Delete the subject
    const subjectsView = page.getByTestId('subjects-view');
    await expect(subjectsView).toBeVisible();
    await subjectsView.getByTestId('add-filter-button').click();

    // Modify filters dialog
    const modifyFiltersDialog2 = page.getByTestId('modify-filters-dialog');
    await expect(modifyFiltersDialog2).toBeVisible();
    await modifyFiltersDialog2.getByPlaceholder('Add new filter...').fill('subject');
    // Click the option that shows "Subject" (variable name; MUI Autocomplete uses role="option")
    await page.getByRole('option', { name: /Subject/ }).first().click();

    await modifyFiltersDialog.getByPlaceholder('Search').fill('p1-e2e');
    await page.waitForTimeout(500);
    await page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first().click();

    await modifyFiltersDialog.getByTestId('apply-filters-button').click();

    // Dialog should close
    await expect(modifyFiltersDialog).not.toBeVisible();

    await page.waitForLoadState('networkidle');

    // Table should have exactly one data row
    const subjectsView2 = page.getByTestId('subjects-view');
    const dataRows2 = subjectsView2.locator('tbody tr');
    await expect(dataRows2).toHaveCount(1);

    const row2 = dataRows2.first();
    await expect(row2.locator('td').first().locator('a')).toContainText('p1-e2e');
    await expect(row2.locator('td').nth(2)).toContainText('admin');
    const deleteSubjectButton = row2.locator('td').last().locator('[aria-label="Delete subject"]');
    await expect(deleteSubjectButton).toBeVisible();

    await deleteSubjectButton.click();
    // Delete confirmation dialog
    const deleteDialog2 = page.getByTestId('delete-dialog');
    await expect(deleteDialog2).toBeVisible();
    await deleteDialog2.getByTestId('delete-button').click();

    await page.waitForLoadState('networkidle');

    // Subject link should no longer exist
    const subjectsView3 = page.getByTestId('subjects-view');
    await expect(subjectsView3.locator('a').filter({ hasText: 'p1-e2e' })).toHaveCount(0);

  });
});
