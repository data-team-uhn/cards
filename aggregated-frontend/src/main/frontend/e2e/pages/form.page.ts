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

export class FormPage {
  readonly page: Page;
  readonly saveButton: Locator;
  readonly saveAndViewButton: Locator;
  readonly editButton: Locator;
  readonly moreActionsButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Inject component
    this.saveButton = page.getByRole('button', { name: 'Save', exact: true });
    this.saveAndViewButton = page.getByRole('button', { name: 'Save and view' });
    this.editButton = page.getByRole('button', { name: 'Edit' });
    this.moreActionsButton = page.getByRole('button', { name: 'More actions' });
  }

  async expectLoadedForEdit() {
    await this.page.waitForLoadState('networkidle');
    //await expect(this.page.url()).toMatch(/\/content\.html\/Forms\/[^/]+\.edit$/);
    await expect(this.saveButton).toBeVisible();
    await expect(this.saveAndViewButton).toBeVisible();
    await expect(this.moreActionsButton).toBeVisible();
  }

  async answerQuestions(questionnaireName: string, fields: Record<string, any>, path: string | null) {

    for (const [key, fieldSpec] of Object.entries(fields)) {
  
      const fullpath = path ? `${path}/${key}` : key;
      const container = this.page.locator(`[id="/Questionnaires/${questionnaireName}/${fullpath}"]`);
  
      if (fieldSpec.checkCondition) {
        const { checkCondition, ...rest } = fieldSpec;
  
        for (const condition of fieldSpec.checkCondition) {
          // answer question to trigger condition
          await this.answerQuestion(container, {...rest, name: condition.name}, key);
          // condition.verify.field is absolute path to the question element
          const condLoc = this.page.locator(`[id="/Questionnaires/${questionnaireName}/${condition.verify.field}"]`);

          if (condition.verify.visible) {
            await expect(condLoc).toBeVisible();
          } else {
            await expect(condLoc).toBeHidden();
          }
        }
      } else {
        await this.answerQuestion(container, fieldSpec, key);
      }
    }
  }

  async saveAndView() {
    await this.saveAndViewButton.click();
    await this.page.waitForLoadState('networkidle');

    await expect(this.editButton).toBeVisible();
    await expect(this.moreActionsButton).toBeVisible();
  }

  async save() {
    await this.saveButton.click();
    await this.page.waitForLoadState('networkidle');

    await expect(this.saveButton).toBeVisible();
  }

  async delete() {
    await this.moreActionsButton.click();
    await this.page.getByRole('menuitem', { name: 'Delete' }).click();
    await this.page.waitForLoadState('networkidle');

    await expect(this.page.url()).toMatch(/\/content\.html\/Questionnaires\/User$/);
  }
  
  async changeSubjectToExisting(subjectId: string) {
    await this.moreActionsButton.click();
    await this.page.getByRole('menuitem', { name: 'Change subject' }).click();

    // TODO: implement: add component for set subject dialog
    const setSubjectDialog = this.page.locator('[aria-label="set-subject-dialog"]');
    await expect(setSubjectDialog).toBeVisible();
    await setSubjectDialog.getByRole('textbox', { name: 'Search' }).fill('Patient 1');
    // ...select the existing subject
    await setSubjectDialog.getByRole('cell', { name: 'Patient ' + subjectId }).click();
    // continue
    await setSubjectDialog.getByRole('button', { name: 'Continue' }).click();
    // close dialog
    await setSubjectDialog.getByRole('button', { name: 'Close' }).click();
    await expect(setSubjectDialog).not.toBeVisible({ timeout: 5000 });
    await this.page.waitForLoadState('networkidle');
  }

  async changeSubjectToNew(subjectId: string) {
    await this.moreActionsButton.click();
    await this.page.getByRole('menuitem', { name: 'Change subject' }).click();

    // TODO: implement: add component for set subject dialog
    const setSubjectDialog = this.page.locator('[aria-label="set-subject-dialog"]');
    await expect(setSubjectDialog).toBeVisible();
    //...fill in new subject details
    await setSubjectDialog.getByRole('button', { name: 'Close' }).click();
    await expect(setSubjectDialog).not.toBeVisible({ timeout: 5000 });
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Generate a token for the subject.
   * @param {string} subjectId - id of the subject
   * @returns {string} token
   */
  async generateTokenForSubject(subjectId: string) {
    await this.page.getByRole('link', { name: subjectId }).click();
    await this.page.waitForLoadState('networkidle');
    const rest = this.page.url().split('/Subjects/')[1];
    await this.page.goto('/Subjects/' + rest + '.token.html');
    await this.page.waitForLoadState('networkidle');

    const token = await this.page.locator('body').innerText();
    return token;
  }


  //---HELPER FUNCTIONS---
  
/**
 * Answer question from container based on its type (role) (combobox, radio, textbox).
 *
 * @param {import('@playwright/test').Locator} container
 * @param {{ role: string, option?: string, name?: string, exact?: boolean, value?: string }} fieldSpec
 * @param {string} fieldKey field id (for errors)
 */
async answerQuestion(container: Locator, fieldSpec: { role: string, option?: string, name?: string, exact?: boolean, value: string }, fieldKey: string) {
    switch (fieldSpec.role) {
      case 'combobox':
        await container.getByRole('combobox').click();
        await this.page.getByRole('option', { name: fieldSpec.option }).click();
        break;
      case 'radio': {
        const roleOpts: { name?: string; exact?: boolean } = { name: fieldSpec.name };
        if (fieldSpec.exact !== undefined) {
          roleOpts.exact = fieldSpec.exact;
        }
        await container.getByRole('radio', roleOpts).click();
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

  async answerSections(questionnaireName: string, data: Record<string, any>, path: string | null, isPatientPortalTest: boolean) {
    const sectionKeys = Object.keys(data.sections);
  
    for (let i = 0; i < sectionKeys.length; i++) {
      const sectionKey = sectionKeys[i];
      const sectionPath = path ? `${path}/${sectionKey}/` : sectionKey;
      const sectionSpec = data.sections[sectionKey];
  
      if (sectionSpec.heading) {
        const h = sectionSpec.heading;
        await expect(this.page.getByRole('heading', { name: h.name })).toContainText(h.text);
      }
  
      await this.answerQuestions(questionnaireName, sectionSpec.fields, sectionPath);
  
      if (isPatientPortalTest) {
        const isLast = i === sectionKeys.length - 1;
        if (isLast) {
          await this.page.getByRole('button', { name: 'Submit' }).click();
          await expect(this.page.locator('h4')).toContainText('Thank you');
        } else {
          if (sectionSpec.expectFooterNext) {
            await expect(this.page.locator('#cards-resource-footer')).toContainText('Next');
          }
          await this.page.getByRole('button', { name: 'Next' }).click();
          await this.page.waitForLoadState('networkidle');
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
   * @param {Record<string, object>} data
   */
  async fillSurveyModules(data: Record<string, any>) {
    if (data.fields) {
      await this.answerQuestions(data.questionnaireName, data.fields, null);
    }

    if (data.sections) {
      await this.answerSections(data.questionnaireName, data, null, data.isPatientPortalTest);
    }
  }
}
