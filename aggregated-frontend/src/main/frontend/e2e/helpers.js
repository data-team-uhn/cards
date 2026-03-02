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

const { expect } = require('@playwright/test');

/**
 * Log in as admin (username/password admin/admin) and wait for dashboard.
 * @param {import('@playwright/test').Page} page
 */
async function loginAsAdmin(page) {
  await page.goto('/');
  await page.waitForLoadState('networkidle');

  await page.locator('input[name="j_username"], input#j_username').fill('admin');
  await page.locator('input[name="j_password"], input#j_password').fill('admin');

  await page.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Login")').click();

  await page.waitForURL(/\/content\.html\/Questionnaires\/User|\/$/, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  expect(page.url()).toMatch(/\/content\.html\/Questionnaires\/User/);
}

/**
 * Click the first visible element in the locator set.
 * @param {import('@playwright/test').Locator} locator
 */
async function clickFirstVisible(locator) {
  const first = locator.filter({ visible: true }).first();
  await first.click();
}

/**
 * Sign out: open user menu (avatar or drawer) then click sign out; assert anonymous session.
 * @param {import('@playwright/test').Page} page
 * @param {{ mode: 'wide' | 'narrow' }} options - 'wide': use avatar button; 'narrow': use open drawer button
 */
async function logout(page, { mode }) {
  if (mode === 'wide') {
    await clickFirstVisible(page.locator('[aria-label="admin-avatar"]'));
  } else if (mode === 'narrow') {
    await page.locator('[aria-label="open drawer"]').click();
  } else {
    throw new Error(`Unknown logout mode: ${mode}`);
  }

  await clickFirstVisible(page.locator('[aria-label="admin-signout"]'));

  await page.waitForURL(/\/login|\/$/, { timeout: 10000 });
  await page.waitForLoadState('networkidle');

  const logoutSessionResponse = await page.request.get('/system/sling/info.sessionInfo.json');
  expect(logoutSessionResponse.ok()).toBeTruthy();
  const logoutSessionData = await logoutSessionResponse.json();
  expect(logoutSessionData.userID).toBe('anonymous');
}

/**
 * Try to delete the form for the given subject (Questionnaires/User, forms view).
 * No-op if no form is found. Caller should be logged in.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 */
async function deleteFormForSubject(page, subjectId) {
  // Go to Questionnaires/User dashboard
  await page.goto('/content.html/Questionnaires/User');
  await page.waitForLoadState('networkidle');

  // Go to Forms view
  const formsView = page.locator('[aria-label="forms-view"]');
  if (!(await formsView.isVisible())) return;

  // Modify filters dialog
  await formsView.locator('[aria-label="add-filter-button"]').click();
  const modifyFiltersDialog = page.locator('[aria-label="modify-filters-dialog"]');
  await expect(modifyFiltersDialog).toBeVisible({ timeout: 5000 });
  await modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');

  // Click the option that shows "Subject" (variable name; MUI Autocomplete uses role="option")
  await page.getByRole('option', { name: /Subject/ }).first().click();
  await modifyFiltersDialog.getByPlaceholder('Search').fill(subjectId);
  await page.waitForTimeout(500);
  const dropdownItem = page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first();
  if (await dropdownItem.isVisible()) await dropdownItem.click();
  await modifyFiltersDialog.locator('[aria-label="apply-filters-button"]').click();

  // Dialog should close
  await expect(modifyFiltersDialog).not.toBeVisible({ timeout: 5000 });
  await page.waitForLoadState('networkidle');

  // Table should have exactly one data row
  const formsView2 = page.locator('[aria-label="forms-view"]');
  const dataRows = formsView2.locator('tbody tr');
  await expect(dataRows).toHaveCount(1);

  // Check that the row contains the subject id
  const row = dataRows.first();
  await expect(row.locator('td').first().locator('a')).toHaveText(new RegExp(`^${subjectId} :`));
  // Column that "created by" column cell contains "admin"
  await expect(row.locator('td').nth(2)).toContainText('admin');
  // Last cell: Actions column contains edit and delete buttons
  const lastTd = row.locator('td').last();
  await expect(lastTd.locator('a[aria-label="Edit form"]')).toBeVisible();
  const deleteFormButton = lastTd.locator('[aria-label="Delete form"]');
  await expect(deleteFormButton).toBeVisible();

  // Delete confirmation dialog
  await deleteFormButton.click();
  const deleteDialog = page.locator('[aria-label="delete-dialog"]');
  await expect(deleteDialog).toBeVisible({ timeout: 5000 });
  await deleteDialog.locator('[aria-label="delete-button"]').click();
  await page.waitForLoadState('networkidle');

  // Form link should no longer exist
  const formsView3 = page.locator('[aria-label="forms-view"]');
  await expect(formsView3.locator('a').filter({ hasText: `${subjectId} :` })).toHaveCount(0);
};

/**
 * Delete the subject with the given id from the Questionnaires/User dashboard.
 * Assumes the caller is logged in, the subject has no forms, and the subject exists.
 * Deletion is done via applying global filters to the subjects view,
 * locating the subject row, then clicking the delete button on the row.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 */
async function deleteSubjectByIdWithNoForms(page, subjectId) {
  // Go to Questionnaires/User dashboard
  await page.goto('/content.html/Questionnaires/User');
  await page.waitForLoadState('networkidle');
  const subjectsView = page.locator('[aria-label="subjects-view"]');
  if (!(await subjectsView.isVisible())) return;

  // Modify filters dialog
  await subjectsView.locator('[aria-label="add-filter-button"]').click();
  const modifyFiltersDialog = page.locator('[aria-label="modify-filters-dialog"]');
  await expect(modifyFiltersDialog).toBeVisible({ timeout: 5000 });
  await modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');

  // Click the option that shows "Subject" (variable name; MUI Autocomplete uses role="option")
  await page.getByRole('option', { name: /Subject/ }).first().click();
  await modifyFiltersDialog.getByPlaceholder('Search').fill(subjectId);
  await page.waitForTimeout(500);
  const dropdownItem = page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first();
  if (await dropdownItem.isVisible()) await dropdownItem.click();
  await modifyFiltersDialog.locator('[aria-label="apply-filters-button"]').click();

  // Dialog should close
  await expect(modifyFiltersDialog).not.toBeVisible({ timeout: 5000 });
  await page.waitForLoadState('networkidle');

  // Table should have exactly one data row
  const subjectsView2 = page.locator('[aria-label="subjects-view"]');
  const dataRows = subjectsView2.locator('tbody tr');
  await expect(dataRows).toHaveCount(1);

  const row2 = dataRows.first();
  await expect(row2.locator('td').first().locator('a')).toContainText(subjectId);
  await expect(row2.locator('td').nth(2)).toContainText('admin');

  const deleteSubjectButton = row2.locator('td').last().locator('[aria-label="Delete subject"]');
  await expect(deleteSubjectButton).toBeVisible();
  await deleteSubjectButton.click();

  // Delete confirmation dialog
  const deleteDialog = page.locator('[aria-label="delete-dialog"]');
  await expect(deleteDialog).toBeVisible({ timeout: 5000 });
  await deleteDialog.locator('[aria-label="delete-button"]').click();
  await page.waitForLoadState('networkidle');

  // Subject link should no longer exist
  const subjectsView3 = page.locator('[aria-label="subjects-view"]');
  await expect(subjectsView3.locator('a').filter({ hasText: subjectId })).toHaveCount(0);
}

module.exports = {
  loginAsAdmin,
  clickFirstVisible,
  logout,
  deleteFormForSubject,
  deleteSubjectByIdWithNoForms,
};
