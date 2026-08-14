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

export class HomePage {
  readonly page: Page;
  readonly dashboard: Dashboard;

  constructor(page: Page) {
    this.page = page;
    this.dashboard = new Dashboard(page);
  }

  async goto() {
    await this.page.goto('/content.html/Questionnaires/User');
    await this.page.waitForLoadState('networkidle');
  }

  async expectLoaded() {
    await this.page.waitForLoadState('networkidle');
    await expect(this.page).toHaveURL('/content.html/Questionnaires/User');
    await this.dashboard.expectLoaded();
  }

  async logout() {
    await this.dashboard.logout();
  }
}
