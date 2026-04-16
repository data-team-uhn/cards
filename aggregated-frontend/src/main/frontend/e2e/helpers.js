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

const { expect } = require('@playwright/test');

/**
 * Begins the survey for the given questionnaires.
 * @param {Page} page - The page object.
 * @param {Array} questionnaires - The questionnaires titles to check.
 */
async function beginSurvey(page, questionnaires) {
  // Patient Portal start page checks
  await expect(page.getByRole('heading', { name: 'Your Experience at UHN', exact: true })).toBeVisible();

  // The backend survey can arrive late after token generation; retry with reloads.
  const allQuestionnairesVisible = async () => {
    const checks = await Promise.all(
      questionnaires.map(async (questionnaire) => {
        const count = await page.getByText(questionnaire.title).count();
        return count > 0;
      })
    );
    return checks.every(Boolean);
  };

  let ready = await allQuestionnairesVisible();
  for (let attempt = 1; !ready && attempt <= 3; attempt++) {
    await page.reload();
    await page.waitForLoadState('networkidle');
    ready = await allQuestionnairesVisible();
  }
  if (!ready) {
    const headings = await page.getByRole('heading').allTextContents();
    throw new Error(`Expected questionnaire titles to appear on survey landing page; headings=${JSON.stringify(headings.map((h) => h.trim()))}`);
  }

  for (const questionnaire of questionnaires) {
    await expect(page.getByText(questionnaire.title).first()).toBeVisible();
  }

  await expect(page.getByRole('button', { name: 'Begin' })).toBeVisible();
  await page.getByRole('button', { name: 'Begin' }).click();
  await page.waitForLoadState('networkidle');
}

module.exports = {
  beginSurvey
};
