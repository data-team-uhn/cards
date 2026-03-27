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

import { test, expect } from '@playwright/test';
const {
  loginAsAdmin,
  logout,
  createVisitFormWithGivenPatientSubjectId,
  createPatientInformationFormWithNewSubject,
  deleteSubjectById,
  fillSurveyModules
} = require('./helpers');

/** Patient information test data*/
const PatientInformationFormData = {
  isPatientPortalTest: false,
  questionnaireName: 'Patient information',
  fields: {
    last_name: { role: 'textbox', value: 'Doe' },
    first_name: { role: 'textbox', value: 'John' },
    date_of_birth: { role: 'textbox', value: '2023-01-01' }
  }
};

/** Visit information test data*/
const VisitInformationFormData = {
  isPatientPortalTest: false,
  questionnaireName: 'Visit information',
  fields: {
    clinic: { role: 'combobox', option: 'UHN Emergency Department' },
    surveys_complete: { role: 'radio', name: 'No' },
    surveys_submitted: { role: 'radio', name: 'No' },
    time: { role: 'textbox', name: 'yyyy-mm-dd hh:mm', value: '2026-03-20 00:00' },
    provider: { role: 'textbox', value: 'UHN' },
    location: { role: 'textbox', value: 'UHN Emergency Department' },
    status: { role: 'textbox', value: 'in progress' },
    fhir_id: { role: 'textbox', value: '123123' }
  }
};

/** OED survey test data*/
const EDTestData = {
  isPatientPortalTest: true,
  questionnaireName: 'OED',
  heading: 'Your Experience at UHN',
  ul: 'UHN Patient Experience Emergency Department Survey',
  sections: {
    oed_module1: {
      heading: {
        name: 'During this emergency',
        text: 'During this emergency department visit...'
      },
      fields: {
        oed_1: { role: 'radio', name: 'Yes', exact: true },
        oed_2: { role: 'radio', name: 'Never' },
        oed_3: { role: 'radio', name: 'Never' },
        oed_4: { role: 'radio', name: 'Never' },
        oed_5: { role: 'radio', name: 'Never' },
        oed_6: { role: 'radio', name: 'No', exact: true },
        oed_7: { role: 'radio', name: 'Not at all' },
        oed_8: { role: 'radio', name: 'I had a very poor experience' },
        oed_9: { role: 'radio', name: '- Extremely likely' }
      }
    },
    oed_module2: {
      heading: {
        name: 'Additional questions about',
        text: 'Additional questions about your recent emergency visit'
      },
      fields: {
        oed_11: { role: 'radio', name: 'Always' },
        oed_sqp_1: { role: 'radio', name: 'Never' },
        oed_sqp_2: {
          role: 'radio',
          exact: true,
          checkCondition: [
            { name: 'Yes', verify: { field: 'oed_module2/section_oed_sqp_3/oed_sqp_3', visible: true } },
            { name: 'No', verify: { field: 'oed_module2/section_oed_sqp_3/oed_sqp_3', visible: false } }
          ]
        },
        oed_sqp_4: {
          role: 'radio',
          exact: true,
          checkCondition: [
            { name: 'No', verify: { field: 'oed_module2/section_oed_sqp_5/oed_sqp_5', visible: false } },
            { name: 'Yes', verify: { field: 'oed_module2/section_oed_sqp_5/oed_sqp_5', visible: true } }
          ]
        },
        'section_oed_sqp_5/oed_sqp_5': { role: 'radio', name: 'Never' }
      },
    },
    oed_module3: {
      fields: {
        oed_ic_1: { role: 'radio', name: 'Yes, always' },
        oed_ic_2: { role: 'radio', name: 'Not at all' }
      }
    }
  }
};

test.describe('Create questionnaire form with new subject', () => {
  test.describe.configure({ timeout: 120000 });

  test('Patient Portal: ED visit in jan - march', async ({ page }, testInfo) => {
    await loginAsAdmin(page);

    const subjectId = `p1e2e${testInfo.project.name}`;

    await createPatientInformationFormWithNewSubject(page, subjectId, PatientInformationFormData);

    await page.goto('/content.html/Questionnaires/User');

    await createVisitFormWithGivenPatientSubjectId(page, subjectId, VisitInformationFormData);

    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();
    await expect(page.locator('[id="/Questionnaires/Visit information/clinic"]')).toContainText('UHN Emergency Department');

    await page.getByRole('link', { name: 'visit-' + subjectId }).click();
    await page.waitForLoadState('networkidle');
    const rest = page.url().split('/Subjects/')[1];

    // Generate token
    await page.goto('/Subjects/' + rest + '.token.html');
    const token = await page.locator('body').innerText();

    // Go to Questionnaires/User dashboard to logout
    await page.goto('/content.html/Questionnaires/User');
    await logout(page, { mode: 'wide' });

    await page.goto('/Survey.html?auth_token=' + token);
    await page.waitForLoadState('networkidle');

    // Start filling survey as patient
    await fillSurveyModules(page, EDTestData);

    await loginAsAdmin(page);
    await deleteSubjectById(page, subjectId, true);
  });
});
