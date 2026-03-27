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
  await page.goto('/login');
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
 * @param {{ mode?: 'wide' | 'narrow' }} [options] - 'wide': use avatar button; 'narrow': use open drawer button
 */
async function logout(page, options = {}) {
  const mode = options.mode ?? 'wide';
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
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
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

  const formsView = page.locator('[aria-label="forms-view"]');
  if (!(await formsView.isVisible())) return;

  // Modify filters dialog
  await formsView.locator('[aria-label="add-filter-button"]').click();
  const modifyFiltersDialog = page.locator('[aria-label="modify-filters-dialog"]');
  await expect(modifyFiltersDialog).toBeVisible({ timeout: 5000 });
  await modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');

  // Click the option that shows "Subject" 
  await page.getByRole('option', { name: /Subject/ }).first().click();
  await modifyFiltersDialog.getByPlaceholder('Search').fill(subjectId);
  await page.waitForTimeout(500);
  const dropdownItem = page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first();
  if (await dropdownItem.isVisible()) await dropdownItem.click();
  await modifyFiltersDialog.locator('[aria-label="apply-filters-button"]').click();

  // Dialog should close
  await expect(modifyFiltersDialog).not.toBeVisible({ timeout: 5000 });
  await page.waitForLoadState('networkidle');

  // Verify table has exactly one data row
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
}

/**
 * FLOW
 * New → Questionnaire → search/select questionnaire → continue → new subject → create with identifier.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 * @param {string} questionnaireName
 */
async function createFormAndSubjectWithGivenNamesFirstSteps(page, subjectId, questionnaireName) {
  await page.getByRole('button', { name: 'new' }).click();

  const newItemDialog = page.locator('[aria-label="new-item-dialog"]');
  await expect(newItemDialog).toBeVisible();
  await newItemDialog.locator('td').filter({ hasText: 'Questionnaire' }).click();
  await newItemDialog.locator('[aria-label="new-item-next-button"]').click();

  const newFormDialog = page.locator('[aria-label="new-form-dialog"]');
  await expect(newFormDialog).toBeVisible();

  await page.waitForLoadState('networkidle');

  await newFormDialog.getByRole('textbox', { name: 'Search' }).fill(questionnaireName);
  const firstRow = newFormDialog.locator('tbody tr').first();
  await expect(firstRow).toContainText(questionnaireName);
  await firstRow.click();

  await newFormDialog.locator('[aria-label="new-form-continue-button"]').click();
  await newFormDialog.locator('[aria-label="new-form-new-subject-button"]').click();

  const newSubjectDialog = page.locator('[aria-label="new-subject-dialog"]');
  await newSubjectDialog.locator('[aria-label="new-subject-identifier-input"]').locator('input').fill(subjectId);
  await newSubjectDialog.getByRole('button', { name: 'new-subject-create-button' }).click();

  await page.waitForLoadState('networkidle');
}

/**
 * FLOW
 * Complete new-subject dialog with a new identifier, then select parent patient and continue.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId - parent patient id (search + row selection in select-parent dialog)
 * @param {string} parentSubjectId - value for the new subject identifier field (e.g. `'visit-' + subjectId`)
 */
async function selectSubjectParentByName(page, subjectId) {

  const selectParentDialog = page.locator('[aria-label="select-parent-dialog"]');
  await selectParentDialog.getByRole('textbox', { name: 'Search' }).fill(subjectId);
  await selectParentDialog.getByRole('cell', { name: 'Patient ' + subjectId }).click();
  await selectParentDialog.getByRole('button', { name: 'Continue' }).click();

  await page.waitForLoadState('networkidle');

  // await expect(page.locator('[aria-label="select-parent-dialog"]')).not.toBeVisible({ timeout: 5000 });
  //await page.waitForURL(/\/content\.html\/Forms\/[^/]+\.edit$/, { timeout: 15000 });
}

/**
 * FLOW
 * Save the form (Save and view), wait for load, assert view mode (Edit visible).
 * @param {import('@playwright/test').Page} page
 */
async function saveForm(page) {
  await page.getByRole('button', { name: 'Save and view' }).click();
  await page.waitForLoadState('networkidle');
  await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();
}

/**
 * Create a new form with the given subject id, Save and view, assert form view (Edit visible).
 * Caller must already be logged in.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 */
async function createPatientInformationFormWithNewSubject(page, subjectId, data) {
  await createFormAndSubjectWithGivenNamesFirstSteps(page, subjectId, data.questionnaireName);

  await answerQuestions(page, data.questionnaireName, data.fields, null);

  await saveForm(page);

  //await expect(page.locator('[id="/Questionnaires/Patient information/last_name"]')).toContainText('Doe');
  //await expect(page.locator('[id="/Questionnaires/Patient information/first_name"]')).toContainText('John');
  //await expect(page.locator('[id="/Questionnaires/Patient information/date_of_birth"]')).toContainText('2023-01-01');
}

/**
 * Create a new Visit questionnaire with the given name and subject id; lands on form edit URL.
 * Caller must already be logged in.
 * @param {import('@playwright/test').Page} page
 * @param {string} questionnaireName
 * @param {string} subjectId
 */
async function createVisitFormWithGivenPatientSubjectId(page, subjectId, data) {
  await createFormAndSubjectWithGivenNamesFirstSteps(page, 'visit-' + subjectId, data.questionnaireName);

  await selectSubjectParentByName(page, subjectId);

  await answerQuestions(page, data.questionnaireName, data.fields, null);

  await saveForm(page);
}

/**
 * Delete the subject with the given id from the Questionnaires/User dashboard.
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 * @param {boolean} [hasForms] second delete-dialog confirm when subject has linked forms
 */
async function deleteSubjectById(page, subjectId, hasForms = false) {
  await page.goto('/content.html/Questionnaires/User');
  await page.waitForLoadState('networkidle');
  const subjectsView = page.locator('[aria-label="subjects-view"]');
  if (!(await subjectsView.isVisible())) return;

  await subjectsView.locator('[aria-label="add-filter-button"]').click();
  const modifyFiltersDialog = page.locator('[aria-label="modify-filters-dialog"]');
  await expect(modifyFiltersDialog).toBeVisible({ timeout: 5000 });
  await modifyFiltersDialog.getByPlaceholder('Add new filter...').fill('subject');

  await page.getByRole('option', { name: /Subject/ }).first().click();
  await modifyFiltersDialog.getByPlaceholder('Search').fill(subjectId);
  await page.waitForTimeout(500);
  const dropdownItem = page.locator('li[class*="dropdownItem"]').filter({ visible: true }).first();
  if (await dropdownItem.isVisible()) await dropdownItem.click();
  await modifyFiltersDialog.locator('[aria-label="apply-filters-button"]').click();

  await expect(modifyFiltersDialog).not.toBeVisible({ timeout: 5000 });
  await page.waitForLoadState('networkidle');

  const subjectsView2 = page.locator('[aria-label="subjects-view"]');
  const dataRows = subjectsView2.locator('tbody tr');
  await expect(dataRows).toHaveCount(1);

  const row2 = dataRows.first();
  await expect(row2.locator('td').first().locator('a')).toContainText(subjectId);
  await expect(row2.locator('td').nth(2)).toContainText('admin');

  const deleteSubjectButton = row2.locator('td').last().locator('[aria-label="Delete subject"]');
  await expect(deleteSubjectButton).toBeVisible();
  await deleteSubjectButton.click();

  const deleteDialog = page.locator('[aria-label="delete-dialog"]');
  await expect(deleteDialog).toBeVisible({ timeout: 5000 });
  await deleteDialog.locator('[aria-label="delete-button"]').click();

  if (hasForms) {
    const deleteDialog2 = page.locator('[aria-label="delete-dialog"]');
    await deleteDialog2.getByRole('button', { name: 'delete-button' }).click();
  }

  await page.waitForLoadState('networkidle');

  const subjectsView3 = page.locator('[aria-label="subjects-view"]');
  await expect(subjectsView3.locator('a').filter({ hasText: subjectId })).toHaveCount(0);
}

/**
 * Delete subject when it has no forms (single confirm).
 * @param {import('@playwright/test').Page} page
 * @param {string} subjectId
 */
async function deleteSubjectByIdWithNoForms(page, subjectId) {
  return deleteSubjectById(page, subjectId, false);
}

/**
 * Fill one field container (combobox → option, radio, textbox).
 *
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Locator} container
 * @param {{ role: string, option?: string, name?: string, exact?: boolean, value?: string }} fieldSpec
 * @param {string} fieldKey field id (for errors)
 */
async function answerQuestion(page, container, fieldSpec, fieldKey) {
  switch (fieldSpec.role) {
    case 'combobox':
      await container.getByRole('combobox').click();
      await page.getByRole('option', { name: fieldSpec.option }).click();
      break;
    case 'radio': {
      const roleOpts = { name: fieldSpec.name };
      if (fieldSpec.exact !== undefined) {
        roleOpts.exact = fieldSpec.exact;
      }
      await container.getByRole('radio', roleOpts).check();
      break;
    }
    case 'textbox': {
      const textbox = fieldSpec.name
        ? container.getByRole('textbox', { name: fieldSpec.name })
        : container.getByRole('textbox');
      await textbox.fill(fieldSpec.value);
      break;
    }
    default:
      throw new Error(
        `answerQuestion: unsupported role "${fieldSpec.role}" for field "${fieldKey}"`
      );
  }
}

/**
 * Fill fields on an edit form using ordered steps (combobox → option, radio, textbox).
 *
 * @param {import('@playwright/test').Page} page
 * @param {{
 *   questionnaire: string,
 *   fields: Array<{
 *     id: string,
 *     role: 'combobox' | 'radio' | 'textbox',
 *     option?: string,
 *     name?: string,
 *     exact?: boolean,
 *     value?: string
 *   }>
 * }} spec
 */
async function answerQuestions(page, questionnaireName, fields, path) {

  for (const [key, fieldSpec] of Object.entries(fields)) {

    const fullpath = path ? `${path}/${key}` : key;
    console.log(`[id="/Questionnaires/${questionnaireName}/${fullpath}"]`);
    const container = page.locator(`[id="/Questionnaires/${questionnaireName}/${fullpath}"]`);

    if (fieldSpec.checkCondition) {
      const { checkCondition, ...rest } = fieldSpec;

      for (const condition of fieldSpec.checkCondition) {
        // answer question to trigger condition
        await answerQuestion(page, container, {...rest, name: condition.name}, key);
        // condition.verify.field is absolute path to the question element
        const condLoc = page.locator(`[id="/Questionnaires/${questionnaireName}/${condition.verify.field}"]`);
        if (condition.verify.visible) {
          await expect(condLoc).toBeVisible();
        } else {
          await expect(condLoc).toBeHidden();
        }
      }
    } else {
      await answerQuestion(page, container, fieldSpec, key);
    }
  }
}

async function answerSections(page, questionnaireName, data, path, isPatientPortalTest) {
  const sectionKeys = Object.keys(data.sections);

  for (let i = 0; i < sectionKeys.length; i++) {
    const sectionKey = sectionKeys[i];
    const sectionPath = path ? `${path}/${sectionKey}/` : sectionKey;
    const sectionSpec = data.sections[sectionKey];

    if (sectionSpec.heading) {
      const h = sectionSpec.heading;
      await expect(page.getByRole('heading', { name: h.name })).toContainText(h.text);
    }

    await answerQuestions(page, questionnaireName, sectionSpec.fields, sectionPath);

    if (isPatientPortalTest) {
      const isLast = i === sectionKeys.length - 1;
      if (isLast) {
        await page.getByRole('button', { name: 'Submit' }).click();
        await expect(page.locator('h4')).toContainText('Thank you');
      } else {
        if (sectionSpec.expectFooterNext) {
          await expect(page.locator('#cards-resource-footer')).toContainText('Next');
        }
        await page.getByRole('button', { name: 'Next' }).click();
        await page.waitForLoadState('networkidle');
      }
    }
  }
}

/**
 * Walks a survey or fills a single questionnaire form from JSON-like data.
 *
 * **With `sections`:** patient portal flow — optional `heading` / `ul`, Begin, then each section
 * (Next + networkidle; Submit + Thank you on the last).
 *
 * **Without `sections`:** single edit form — `fields` is an ordered array (same shape as
 * {@link answerQuestions}); no landing/Begin/Next/Submit.
 *
 * In both cases `questionnaireName` is required.
 *
 * @param {import('@playwright/test').Page} page
 * @param {Record<string, object>} data
 */
async function fillSurveyModules(page, data) {
  if (!data || typeof data !== 'object') {
    throw new Error('fillSurveyModules: data must be an object');
  }
  if (data.questionnaireName == null || data.questionnaireName === '') {
    throw new Error('fillSurveyModules: questionnaireName is required');
  }

  // Patient Portal start page checks
  if (data.heading != null) {
    await expect(page.getByRole('heading')).toContainText(data.heading);
  }
  if (data.ul != null) {
    await expect(page.locator('ul')).toContainText(data.ul);
  }
  if (data.heading != null || data.ul != null) {
    await expect(page.getByRole('button', { name: 'Begin' })).toBeVisible();
    await page.getByRole('button', { name: 'Begin' }).click();
    await page.waitForLoadState('networkidle');
  }

  if (data.fields) {
    // Generate answers from fields
    await answerQuestions(page, data.questionnaireName, data.fields, null);
  }

  if (data.sections) {
    await answerSections(page, data.questionnaireName, data, null, data.isPatientPortalTest);
  }
}

module.exports = {
  loginAsAdmin,
  logout,
  deleteFormForSubject,
  deleteSubjectById,
  deleteSubjectByIdWithNoForms,
  createPatientInformationFormWithNewSubject,
  createVisitFormWithGivenPatientSubjectId,
  answerQuestions,
  fillSurveyModules
};
