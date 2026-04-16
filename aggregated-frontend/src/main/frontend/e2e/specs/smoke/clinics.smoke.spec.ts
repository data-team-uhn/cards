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

import { test } from '../../fixtures/visit-information.fixture';
import { logout } from '../../flows/auth/logout.flow';
import { FormPage } from '../../pages/form.page';
import { beginSurvey } from '../../helpers';
import { createAndSaveFormWithNewPatientSubject } from '../../flows/patient-information.flow';
import PatientInformationFormTestData from '../../test-data/internal-forms/patient-information.json' with { type: 'json' };
import { loginAs } from '../../flows/auth/login.flow';
import { users } from '../../config/users';

// Clinics to Questionnaires mapping
import ClinicsToQuestionnaireMapping from '../../test-data/clinics.json' with { type: 'json' };

// Survey form test data
import OAIPTestData from '../../test-data/survey-forms/oaip.json' with { type: 'json' };
import OEDTestData from '../../test-data/survey-forms/oed.json' with { type: 'json' };
import ICTestData from '../../test-data/survey-forms/ic.json' with { type: 'json' };
import RehabTestData from '../../test-data/survey-forms/rehab.json' with { type: 'json' };
import OCPETestData from '../../test-data/survey-forms/ocpe.json' with { type: 'json' };
import PMOOTestData from '../../test-data/survey-forms/pmoo.json' with { type: 'json' };
import YVMTestData from '../../test-data/survey-forms/yvm.json' with { type: 'json' };

const SURVEY_FORM_DATA: Record<string, any> = {
  OAIP: OAIPTestData,
  OED: OEDTestData,
  IC: ICTestData,
  Rehab: RehabTestData,
  OCPE: OCPETestData,
  PMOO: PMOOTestData,
  YVM: YVMTestData
};

const randomDigits = (length: number): string =>
  Array.from({ length }, () => Math.floor(Math.random() * 10)).join('');

test.describe('Patient Portal Tests', () => {
  // One shared patient subject for all clinic tests in this suite
  const patientSubjectId = randomDigits(7);

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

  for (const [clinicKey, clinic] of Object.entries(ClinicsToQuestionnaireMapping)) {
    test.describe(`Test for clinic: ${clinicKey}`, () => {

      const visitSubjectId = randomDigits(10);
      const clinicName = clinic.displayName;
      const visitDate = "2026-02-20 00:00";
      // Passing params to the fixture
      test.use({
        clinicName,
        patientSubjectId,
        visitSubjectId,
        visitDate,
      });

      test(`begin patient portal flow for ${clinicName}`, async ({ adminHomePage, createdVisitSubject: visitSubjectId, browser }) => {
        const adminPage = adminHomePage.page;

        // Generate token for the visit subject from the visit information form
        const formPage = new FormPage(adminPage);
        const token = await formPage.generateTokenForSubject(visitSubjectId);

        await logout(adminPage);

        // fresh browser context (clean cookies/session)
        const patientContext = await browser.newContext();
        const page = await patientContext.newPage();
        try {
          // Go to Survey page
          await page.goto('/Survey.html?auth_token=' + token);
          await page.waitForLoadState('networkidle');

          const surveyData: Record<string, any>[] = [];
          for (const questionnaireName of clinic.questionnaires) {
            const questionnaireData = SURVEY_FORM_DATA[questionnaireName];
            if (!questionnaireData) {
              throw new Error(`Missing survey-form data for questionnaire: ${questionnaireName}`);
            }
            surveyData.push(questionnaireData);
          }

          // Check the beginning page for the given questionnaires and click Begin button
          await beginSurvey(page, surveyData);

          for (const questionnaireData of surveyData) {
            // Fill survey
            const surveyForm = new FormPage(page);
            await surveyForm.fillSurvey(questionnaireData);
          }
        } finally {
          // ensure context is closed if test fails
          await patientContext.close();
        }
      });
    });
  }
});
