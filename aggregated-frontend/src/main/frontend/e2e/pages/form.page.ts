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
  readonly footer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.saveButton = page.getByRole('button', { name: 'Save', exact: true });
    this.saveAndViewButton = page.getByRole('button', { name: 'Save and view' });
    this.editButton = page.getByRole('button', { name: 'Edit' });
    this.moreActionsButton = page.getByRole('button', { name: 'More actions' });
    this.footer = page.locator('#cards-resource-footer');
  }

  private surveyStepNext(): Locator {
    return this.footer.getByRole('button', { name: 'Next', exact: true }).last();
  }

  async expectLoadedForEdit() {
    await this.page.waitForLoadState('networkidle');
    await expect(this.saveAndViewButton).toBeVisible();
    await expect(this.moreActionsButton).toBeVisible();
  }

  async saveAndView() {
    await this.saveAndViewButton.click();
    await this.page.waitForLoadState('networkidle');

    await this.editButton.waitFor({ state: 'visible' });
    await this.moreActionsButton.waitFor({ state: 'visible' });
  }

  async save() {
    await this.saveButton.last().click();
    await this.page.waitForLoadState('networkidle');
  }

  async delete() {
    await this.moreActionsButton.click();
    await this.page.getByRole('menuitem', { name: 'Delete' }).click();
    await this.page.waitForLoadState('networkidle');

    await expect(this.page.url()).toMatch(/\/content\.html\/Questionnaires\/User$/);
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

  /**
   * Answer question from container based on its type (role) (combobox, radio, textbox).
   *
   * @param {import('@playwright/test').Locator} container
   * @param {{ role: string, option?: string, name?: string, exact?: boolean, value?: string, answers?: Array<{ label: string }> }} fieldSpec
   * @param {string} fieldKey field id (for errors)
   */
  async answerQuestion(
    container: Locator,
    fieldSpec: {
      role: string;
      option?: string;
      name?: string;
      exact?: boolean;
      value?: string;
    },
    fieldKey: string
  ) {
    switch (fieldSpec.role) {
      case 'combobox':
        await container.getByRole('combobox').click();
        await this.page.getByRole('option', { name: fieldSpec.option, exact: true }).click();
        break;
      case 'radio': {
        const roleOpts: { name?: string; exact?: boolean } = { name: fieldSpec.name };
        if (fieldSpec.exact !== undefined) {
          roleOpts.exact = fieldSpec.exact;
        }
        const radio = container.getByRole('radio', roleOpts);
        await expect(container).toBeVisible({ timeout: 30000 });
        await expect(radio).toBeVisible({ timeout: 30000 });
        await radio.scrollIntoViewIfNeeded();
        await radio.click({ timeout: 30000 });
        await expect(radio).toBeChecked({ timeout: 30000 });
        break;
      }
      case 'textbox': {
        const textbox = fieldSpec.name
          ? container.getByRole('textbox', { name: fieldSpec.name })
          : container.getByRole('textbox');
        if (fieldSpec.value === undefined) {
          throw new Error(`answerQuestion: textbox requires value for field "${fieldKey}"`);
        }
        await textbox.fill(fieldSpec.value);
        break;
      }
      case 'checkbox': {
        const cbOpts: { name: string; exact?: boolean } = { name: fieldSpec.name! };
        if (fieldSpec.exact !== undefined) {
          cbOpts.exact = fieldSpec.exact;
        }
        await container.getByRole('checkbox', cbOpts).check();
        break;
      }
      default:
        throw new Error(
          `answerQuestion: unsupported role "${fieldSpec.role}" for field "${fieldKey}"`
        );
    }
  }

  async answerSection(questionnaireName: string, section: Record<string, any>) {
    if (section.heading) {
      const h = section.heading;
      await expect(this.page.getByRole('heading', { name: h.name })).toContainText(h.text);
    }

    const sectionId = section.sectionId as string | undefined;

    if (section.verify) {
      const verifies = Array.isArray(section.verify) ? section.verify : [section.verify];
      for (const v of verifies) {
        await this.verify(questionnaireName, v);
      }
    }

    if (section.pastPageConditions) {
      await this.runPastPageConditions(questionnaireName, section.pastPageConditions);
    }

    if (section.answers) {
      await this.answerQuestions(questionnaireName, section.answers, sectionId ?? null);
    }

    if (section.nextPageConditions) {
      await this.runNextPageConditions(questionnaireName, sectionId, section.nextPageConditions);
    }
  }

  /**
   * For each case: apply `set` with keys relative to `sectionId` (same as `answers`),
   * click Next `depth` times, assert `expect` (absolute questionnaire field paths), then Back `depth` times.
   */
  private async runNextPageConditions(
    questionnaireName: string,
    sectionId: string | undefined,
    nextPageConditions: { depth?: number; cases: Array<{ set: Record<string, any>; expect: Array<Record<string, any>> }> }
  ) {
    if (!sectionId) {
      throw new Error('nextPageConditions requires sectionId');
    }
    const depth = nextPageConditions.depth ?? 1;
    for (const c of nextPageConditions.cases) {
      await this.answerQuestions(questionnaireName, c.set, sectionId);
      for (let i = 0; i < depth; i++) {
        const nextBtn = this.surveyStepNext();
        await expect(nextBtn).toBeEnabled({ timeout: 30000 });
        await nextBtn.click();
        await this.page.waitForLoadState('networkidle');
        await this.page.waitForTimeout(2000);
      }
      for (const exp of c.expect) {
        await this.page.waitForTimeout(3000);
        await this.verify(questionnaireName, exp);
        await this.page.waitForLoadState('networkidle');
      }
      for (let i = 0; i < depth; i++) {
        await expect(this.footer.getByRole('button', { name: 'Back' })).toBeEnabled();
        await this.footer.getByRole('button', { name: 'Back' }).click();
        await this.page.waitForLoadState('networkidle');
        await this.page.waitForTimeout(2000);
      }
    }
  }

  /**
   * For each case: Back `depth` times, apply `set` using absolute field paths (keys like `section_appt/yvm_1`),
   * then Next `depth` times to return, then assert `expect` on the current page (absolute paths).
   */
  private async runPastPageConditions(
    questionnaireName: string,
    pastPageConditions: {
      depth: number;
      cases: Array<{
        set: Record<string, any>;
        expect: Array<Record<string, any>>;
        intermediateStepSet?: Record<string, any>;
        intermediateStepIndex?: number;
      }>
    }
  ) {
    const depth = pastPageConditions.depth;
    for (const c of pastPageConditions.cases) {
      // go back to the target past page
      for (let i = 0; i < depth; i++) {
        await expect(this.footer.getByRole('button', { name: 'Back' })).toBeEnabled();
        await this.footer.getByRole('button', { name: 'Back' }).click();
        await this.page.waitForLoadState('networkidle');
      }
      // answer the questions on the target past page
      await this.answerQuestions(questionnaireName, c.set, null);
      // go forward to the current page
      for (let i = 0; i < depth; i++) {
        const nextBtn = this.surveyStepNext();
        await expect(nextBtn).toBeEnabled({ timeout: 30000 });
        await nextBtn.click();
        await this.page.waitForLoadState('networkidle');
        await this.page.waitForTimeout(5000);

        // re-answer the questions on the intermediate pages we go through by next button if necessary
        // questionnaires lose answers after being hidden and shown again
        if (c.intermediateStepSet && c.intermediateStepIndex == i+1) {
          await this.answerQuestions(questionnaireName, c.intermediateStepSet, null);
        }
      }
      // assert the visibility of the fields on the current page based on the changed answer
      for (const exp of c.expect) {
        await this.verify(questionnaireName, exp);
      }
    }
  }

  async answerMatrix(
    container: Locator,
    fieldSpec: { role: 'radio' | 'checkbox'; answers?: Array<{ key: string, label: string }> },
    fieldKey: string)
    {
      const answers = fieldSpec.answers;
      if (!Array.isArray(answers)) {
        throw new Error(`answerQuestion: matrix requires answers[] for field "${fieldKey}"`);
      }
      const rows = container.getByRole('row');
      const rowCount = await rows.count();
      if (rowCount !== answers.length + 1) {
        throw new Error(
          `answerQuestion: matrix row count (${rowCount}) !== answers length (${answers.length}) for field "${fieldKey}"`
        );
      }
      for (let i = 1; i < rowCount; i++) {
        const row = rows.nth(i);
        const answer = answers[i-1];
        await row.getByLabel(answer.label, {exact: true }).click();
      }
  }

  async answerQuestions(questionnaireName: string, answers: Record<string, any>, path: string | null) {
    for (const [key, fieldSpec] of Object.entries(answers)) {
      const fullpath = path ? `${path}/${key}` : key;
      const container = this.page.locator(`[id="/Questionnaires/${questionnaireName}/${fullpath}"]`);

      const visibilityRules = fieldSpec.visibility;
      if (visibilityRules) {
        const { visibility, ...rest } = fieldSpec;

        for (const condition of visibilityRules) {
          // answer question to trigger condition
          await this.answerQuestion(container, {...rest, name: condition.name}, key);
          // Support both legacy verify object and grouped verify array.
          const verifies = Array.isArray(condition.verify) ? condition.verify : [condition.verify];
          for (const verify of verifies) {
            await this.verify(questionnaireName, verify);
          }
        }
      } else {
        if (fieldSpec.isMatrix) {
          await this.answerMatrix(container, fieldSpec, key);
        } else {
          await this.answerQuestion(container, fieldSpec, key);
        }
      }
    }
  }

  /**
   * Verify the visibility of a single field based on the visibility rule.
   * @param {string} questionnaireName - name of the questionnaire
   * @param {Record<string, any>} verify - visibility rule
   */
  async verify(questionnaireName: string, verify: Record<string, any>) {
    const condLoc = this.page.locator(`[id="/Questionnaires/${questionnaireName}/${verify.field}"]`);
    if (verify.visible) {
      await expect(condLoc).toBeVisible();
    } else {
      await expect(condLoc).toBeHidden();
    }
  }

  /**
   * Walks a survey or fills a single questionnaire form from JSON-like data.
   * @param {Record<string, object>} data
   */
  async fillSurvey(data: Record<string, any>) {
    data.isPatientPortalTest ? await expect(
        this.page.locator('#patient-portal-header').getByRole('heading', { name: data.title }).first()
      ).toBeVisible() : await expect(
        this.page.getByLabel('resource-title').getByRole('heading', { name: data.title }).first()
      ).toBeVisible();

    if (Array.isArray(data.testFlow)) {
    for (let i = 0; i < data.testFlow.length; i++) {
      const step = data.testFlow[i];
        switch (step.type) {
          case 'section':
            await this.answerSection(data.questionnaireName, step);
            break;
          default:
            throw new Error(`Unsupported step type: ${step.type}`);
        }
        // Click Next button or finish the questionnaire
        const isLast = i === data.testFlow.length - 1;
        if (isLast) {
          const submitBtn = this.footer.getByRole('button', { name: 'Submit' });
          const nextSurveyBtn = this.footer.getByRole('button', { name: 'Next survey' });
          if (await submitBtn.isVisible()) {
            await expect(submitBtn).toBeEnabled();
            await submitBtn.click();
            await this.page.waitForLoadState('networkidle');
            // Patient portal records submission then swaps loading → summary; networkidle can return early.
            await expect(
              this.page.getByRole('heading', { name: 'Thank you', exact: true })
            ).toBeVisible({ timeout: 60000 });
          } else if (await nextSurveyBtn.isVisible()) {
            await expect(nextSurveyBtn).toBeEnabled();
            await nextSurveyBtn.click();
            await this.page.waitForLoadState('networkidle');
          } else {
            if (data.isPatientPortalTest) {
              throw new Error('Last section: neither Submit nor Next survey button is visible in footer');
            }
          }
        } else {
          const nextBtn = this.surveyStepNext();
          await expect(nextBtn).toBeEnabled({ timeout: 30000 });
          await nextBtn.click();
          await this.page.waitForLoadState('networkidle');
        }
      }
      return;
    }
  }
}
