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

import { test, expect } from '../../fixtures/auth.fixture';
import { logout } from '../../flows/auth/logout.flow';
import { loginAs } from '../../flows/auth/login.flow';
import { users } from '../../config/users';
import { FormPage } from '../../pages/form.page';
import { HomePage } from '../../pages/home.page';
import { beginSurvey } from '../../helpers';
import { createPatientInformationFormWithNewSubject } from '../../flows/PI.flow';
import { createVisitFormWithGivenPatientSubjectId } from '../../flows/VI.flow';

import PatientInformationFormTestData from '../../test-data/internal-forms/patient-information.json' with { type: 'json' };
import VisitInformationFormTestData from '../../test-data/internal-forms/visit-information.json' with { type: 'json' };
import EDTestData from '../../test-data/survey-forms/ed.json' with { type: 'json' };

test.describe('Patient Portal Tests', () => {
  let subjectIdForCleanup: string | undefined;

  test.describe.configure({ timeout: 120000 });

  test.afterEach(async ({ page }) => {
    if (!subjectIdForCleanup) return;
    try {
      // Cleanup
      await loginAs(page, users.admin);
      const homePage = new HomePage(page);
      await homePage.expectLoaded();
      // Delete Patientsubject with all forms and child subjects
      await homePage.subjectsView.deletePatientSubjectById(subjectIdForCleanup, true);
    } catch (e) {
      console.error('Post-failure cleanup failed:', e);
    }
  });

  test('Patient Portal: ED visit in jan - march', async ({ adminHomePage }, testInfo) => {
    const page = adminHomePage.page;
    const subjectId = `p1e2e${testInfo.project.name}`;
    subjectIdForCleanup = subjectId;

    await createPatientInformationFormWithNewSubject(page, subjectId, PatientInformationFormTestData);

    await page.goto('/content.html/Questionnaires/User');

    await createVisitFormWithGivenPatientSubjectId(page, subjectId, VisitInformationFormTestData);

    // Verify visit information form was created
    await expect(page.locator('[id="/Questionnaires/Visit information/clinic"]')).toContainText('UHN Emergency Department');

    const formPage = new FormPage(page);
    const token = await formPage.generateTokenForSubject('visit-' + subjectId);

    await logout(page);

    // Go to Survey page
    await page.goto('/Survey.html?auth_token=' + token);
    await page.waitForLoadState('networkidle');

    // Start filling survey as patient
    await beginSurvey(page, EDTestData);

    // Fill survey
    const surveyForm = new FormPage(page);
    await surveyForm.fillSurveyModules(EDTestData);
  });
});
