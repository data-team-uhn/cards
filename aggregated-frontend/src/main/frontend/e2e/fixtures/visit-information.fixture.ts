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

/**
 * Fixture for visit subjects used by patient-portal tests.
 * Created and deletes Visit Information form for a given clinic, patient and visit subject ids.
 * Assumes Patient Information is created externally (in beforeAll hook).
 */
import { test as base, expect } from '@playwright/test';
import { loginAs } from '../flows/auth/login.flow';
import { users } from '../config/users';
import { HomePage } from '../pages/home.page';

import { createAndSaveVisitFormWithNewVisitSubjectId } from '../flows/visit-information.flow';
import VisitInformationFormTestData from '../test-data/internal-forms/visit-information.json' with { type: 'json' };

type VisitSubjectFixtures = {
  createdVisitSubject: string;
};

type VisitSubjectWorkerFixtures = {
  adminHomePage: HomePage;
};

type VisitSubjectOptions = {
  clinicName: string;
  patientSubjectId: string;
  visitSubjectId: string;
  visitDate: string | null;
};

export const test = base.extend<VisitSubjectFixtures & VisitSubjectOptions, VisitSubjectWorkerFixtures>({
  clinicName: ['', { option: true }],
  patientSubjectId: ['', { option: true }],
  visitSubjectId: ['', { option: true }],
  visitDate: ['', { option: true }],
  adminHomePage: [async ({ browser }, use) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    const homePage = new HomePage(page);
    try {
      await use(homePage);
    } finally {
      await page.close();
      await context.close();
    }
  }, { scope: 'worker' }],

  createdVisitSubject: async ({ adminHomePage, patientSubjectId, visitSubjectId, clinicName, visitDate }, use) => {
    const page = adminHomePage.page;
    await adminHomePage.goto();

    // Create fill and save visit informaton for clinic form with the given patient and visit subject ids
    await createAndSaveVisitFormWithNewVisitSubjectId(
      page,
      patientSubjectId,
      visitSubjectId,
      clinicName,
      visitDate,
      VisitInformationFormTestData,
    );
    // Verify the right visit information form was created for the given clinic
    await expect(page.locator('[id="/Questionnaires/Visit information/clinic"]')).toContainText(clinicName);

    await use(visitSubjectId);

    // Delete visit subject with all forms
    await loginAs(page, users.admin);
    const homePage = new HomePage(page);
    await homePage.expectLoaded();
    await homePage.subjectsView.deleteSubjectByIdWithForms(visitSubjectId, 'visit');
  },
});

export { expect };
