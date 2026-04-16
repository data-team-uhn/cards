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

import { Page } from '@playwright/test';
import { FormPage } from '../pages/form.page';   
import { HomePage } from '../pages/home.page';


/**
 * Get the yesterday's date in the required format
 * @returns The yesterday's date in the format of yyyy-MM-dd HH:mm
 */
function getYesterdayInRequiredFormat(): string {
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);

  const year = yesterday.getFullYear();
  const month = String(yesterday.getMonth() + 1).padStart(2, '0');
  const day = String(yesterday.getDate()).padStart(2, '0');
  const hours = String(yesterday.getHours()).padStart(2, '0');
  const minutes = String(yesterday.getMinutes()).padStart(2, '0');

  return `${year}-${month}-${day} ${hours}:${minutes}`;
}

/**
 * Create and save a new form with the existing patient subject id and new visit subject id
 * @param {Page} page - The page object
 * @param {string} patientSubjectId - The patient subject id (subject already created)
 * @param {string} visitSubjectId - The visit subject id (subject to be created)
 * @param {string} clinicName - The clinic name
 * @param {string | null} visitDate - The visit date (optional)
 * @param {Record<string, any>} data - The form answers data object
 */
export async function createAndSaveVisitFormWithNewVisitSubjectId(
    page: Page,
    patientSubjectId: string,
    visitSubjectId: string,
    clinicName: string,
    visitDate: string | null,
    data: { questionnaireName: string, answers: Record<string, any> },
) {
    const homePage = new HomePage(page);
    await homePage.expectLoaded();

    await homePage.createFormWithNewSubject(data.questionnaireName, visitSubjectId);

    await homePage.selectPatientSubjectParentByName(patientSubjectId);

    const formPage = new FormPage(page);
    await formPage.expectLoadedForEdit();
    const formData = structuredClone(data);
    if (formData.answers?.clinic) {
      formData.answers.clinic.option = clinicName;
    }
    if (formData.answers?.time) {
      formData.answers.time.value = visitDate || getYesterdayInRequiredFormat();
    }
    await formPage.answerQuestions(formData.questionnaireName, formData.answers, null);
    await formPage.saveAndView();
}
