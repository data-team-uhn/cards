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

async function beginSurvey(page, data) {
  if (!data || typeof data !== 'object') {
      throw new Error('fillSurveyModules: data must be an object');
  }
  if (data.questionnaireName == null || data.questionnaireName === '') {
    throw new Error('fillSurveyModules: questionnaireName is required');
  }

  // Patient Portal start page checks
  if (data.heading != null) {
    await expect(page.getByRole('heading')).toContainText(data.heading);
  }
  if (data.ul != null) {
    await expect(page.locator('ul')).toContainText(data.ul);
  }
  if (data.heading != null || data.ul != null) {
    await expect(page.getByRole('button', { name: 'Begin' })).toBeVisible();
    await page.getByRole('button', { name: 'Begin' }).click();
    await page.waitForLoadState('networkidle');
  }
}

module.exports = {
  beginSurvey,
};
