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

import { expect, Page } from '@playwright/test';
import { Dashboard } from '../components/page-elements/dashboard.component';
import { FormsView } from '../components/page-elements/forms-view.component';
import { SubjectsView } from '../components/page-elements/subjects-view.component';
import { CreateNewItem } from '../components/page-elements/new-item.component';

export class HomePage {
  readonly page: Page;
  readonly dashboard: Dashboard;
  readonly formsView: FormsView;
  readonly subjectsView: SubjectsView;
  readonly createNewItem: CreateNewItem;

  constructor(page: Page) {
    this.page = page;

    // Inject component
    this.dashboard = new Dashboard(page);
    this.formsView = new FormsView(page); //page.locator('[aria-label="forms-view"]');
    this.subjectsView = new SubjectsView(page); //page.locator('[aria-label="subjects-view"]');
    this.createNewItem = new CreateNewItem(page);
  }

  async goto() {
    await this.page.goto('/content.html/Questionnaires/User');
    await this.page.waitForLoadState('networkidle');
  }

  async expectLoaded() {
    await this.page.waitForLoadState('networkidle');
    await expect(this.page).toHaveURL('/content.html/Questionnaires/User');
    await this.dashboard.expectLoaded();
    await this.formsView.expectVisible();
    await this.subjectsView.expectVisible();
    await this.createNewItem.expectVisible();
  }

  async logout() {
    await this.dashboard.logout();
  }

  async gotoAdministration() {
    await this.page.getByRole('link', { name: 'Administration' }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async createFormWithNewSubject(questionnaireName: string, subjectId: string) {
    await this.createNewItem.createFormWithNewSubject(questionnaireName, subjectId);
  }

  async selectPatientSubjectParentByName(subjectId: string) {
    await this.createNewItem.selectPatientSubjectParentByName(subjectId);
  }

  async createParentSubjectForGivenSubject(subjectId: string) {
    await this.createNewItem.createParentSubjectForGivenSubject(subjectId);
  }

  async deleteFormBySubject(subjectId: string) {
    await this.formsView.deleteFormBySubject(subjectId);
  }
}
