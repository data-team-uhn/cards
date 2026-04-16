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

import { expect, test } from '../../fixtures/visit-information.fixture';
import { logout } from '../../flows/auth/logout.flow';
import { FormPage } from '../../pages/form.page';
import { beginSurvey } from '../../helpers';
import { createAndSaveFormWithNewPatientSubject } from '../../flows/patient-information.flow';
import PatientInformationFormTestData from '../../test-data/internal-forms/patient-information.json' with { type: 'json' };
import { loginAs } from '../../flows/auth/login.flow';
import { users } from '../../config/users';

// Survey form test data
import OAIPTestData from '../../test-data/survey-forms/oaip.json' with { type: 'json' };
import OEDTestData from '../../test-data/survey-forms/oed.json' with { type: 'json' };

const SURVEY_FORM_DATA: Record<string, any> = {
  OAIP: OAIPTestData,
  OED: OEDTestData
};

const CLINIC = {
  "displayName": "UHN Emergency and Inpatient",
  "questionnaires": ["OED", "OAIP"]
}

test.describe('Partial survey filled survey is marked correctly', () => {
  // One shared patient subject for all clinic tests in this suite
  const patientSubjectId = `${Date.now().toString().slice(3, 10)}`;

  test.beforeAll(async ({ adminHomePage }) => {
    const page = adminHomePage.page;
    await loginAs(page, users.admin);
    await createAndSaveFormWithNewPatientSubject(page, patientSubjectId, PatientInformationFormTestData);
  });

  test.afterAll(async ({ adminHomePage }) => {
    const page = adminHomePage.page;
    await loginAs(page, users.admin);
    await adminHomePage.expectLoaded();
    await adminHomePage.subjectsView.deleteSubjectByIdWithForms(patientSubjectId, 'patient');
  });

    test.describe(`Test for clinic: ${CLINIC.displayName}`, () => {

      const visitSubjectId = `${patientSubjectId}${Date.now().toString().slice(7, 10)}`;
      const clinicName = CLINIC.displayName;
      // Passing params to the fixture
      test.use({
        clinicName,
        patientSubjectId,
        visitSubjectId,
      });

      test(`begin patient portal flow for ${clinicName}`, async ({ adminHomePage, createdVisitSubject: visitSubjectId, browser }) => {
        const adminPage = adminHomePage.page;

        // Generate token for the visit subject from the visit information form
        const formPage = new FormPage(adminPage);
        const token = await formPage.generateTokenForSubject(visitSubjectId);

        await logout(adminPage);

        const patientContext = await browser.newContext();
        const page = await patientContext.newPage();
        try {
          // Go to Survey page
          await page.goto('/Survey.html?auth_token=' + token);
          await page.waitForLoadState('networkidle');

          const surveyData: Record<string, any>[] = [];
          for (const questionnaireName of CLINIC.questionnaires) {
            const questionnaireData = SURVEY_FORM_DATA[questionnaireName];
            if (!questionnaireData) {
              throw new Error(`Missing survey-form data for questionnaire: ${questionnaireName}`);
            }
            surveyData.push(questionnaireData);
          }

          // Check the beginning page for the given questionnaires and click Begin button
          await beginSurvey(page, surveyData);

          // Fill survey
          const surveyForm = new FormPage(page);
          await surveyForm.fillSurvey(OEDTestData);
          await page.waitForLoadState('networkidle');
          await page.waitForTimeout(5000);

          // Check the survey is marked as partially filled
          await page.reload();

          await expect(page.getByRole('heading', { name: 'Your Experience at UHN', exact: true })).toBeVisible();
          await expect(page.getByRole('button', { name: 'Begin' })).toBeVisible();

          const listItem = await page.getByRole('listitem').filter({ hasText: OEDTestData.title});
          await expect(listItem.getByTestId('DoneIcon')).toBeVisible();

          const listItem2 = await page.getByRole('listitem').filter({ hasText: OAIPTestData.title});
          await expect(listItem2.getByText('2', { exact: true })).toBeVisible();
        } finally {
          await patientContext.close();
        }
      });
    });

});
