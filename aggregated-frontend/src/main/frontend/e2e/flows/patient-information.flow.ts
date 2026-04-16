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
 * Create and save a new form with a new patient subject
 * @param {Page} page - The page object
 * @param {string} subjectId - The patient subject id (subject to be created)
 * @param {Record<string, any>} data - The form answers data object
 */
export async function createAndSaveFormWithNewPatientSubject(page: Page, subjectId: string, data: { questionnaireName: string, answers: Record<string, any> }) {
    
    const homePage = new HomePage(page);
    await homePage.expectLoaded();
    await homePage.createFormWithNewSubject(data.questionnaireName, subjectId);

    const formPage = new FormPage(page);
    await formPage.expectLoadedForEdit();
    await formPage.answerQuestions(data.questionnaireName, data.answers, null);
    await formPage.saveAndView();
}
