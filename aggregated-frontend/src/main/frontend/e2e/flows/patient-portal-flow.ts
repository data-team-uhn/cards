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

import { Browser, Page } from '@playwright/test';
import { beginSurvey } from '../helpers';
import { logout } from './auth/logout.flow';
import { FormPage } from '../pages/form.page';

const getSurveyData = (questionnaireNames: string[], surveyFormData: Record<string, any>): Record<string, any>[] => {
  const surveyData: Record<string, any>[] = [];
  for (const questionnaireName of questionnaireNames) {
    const questionnaireData = surveyFormData[questionnaireName];
    if (!questionnaireData) {
      throw new Error(`Missing survey-form data for questionnaire: ${questionnaireName}`);
    }
    surveyData.push(questionnaireData);
  }
  return surveyData;
};

export const runPatientPortalFlow = async (
  adminPage: Page,
  browser: Browser,
  visitSubjectId: string,
  questionnaireNames: string[],
  surveyFormData: Record<string, any>
) => {
  // Generate token for the visit subject from the visit information form
  const formPage = new FormPage(adminPage);
  const token = await formPage.generateTokenForSubject(visitSubjectId);

  await logout(adminPage);

  // Fresh browser context for clean cookies/session.
  const patientContext = await browser.newContext();
  const page = await patientContext.newPage();
  try {
    await page.goto('/Survey.html?auth_token=' + token);
    await page.waitForLoadState('networkidle');

    const surveyData = getSurveyData(questionnaireNames, surveyFormData);
    await beginSurvey(page, surveyData);

    for (const questionnaireData of surveyData) {
      const surveyForm = new FormPage(page);
      await surveyForm.fillSurvey(questionnaireData);
    }
  } finally {
    await patientContext.close();
  }
};
