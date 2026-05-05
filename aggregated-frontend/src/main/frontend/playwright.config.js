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

const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // retry failed tests 2 times in continuous Integration to reduce random red builds
  retries: process.env.CI ? 2 : 0,
  // use 1 separate test runner process to avoid interference between unisolated tests
  workers: 1,
  // auto-open the HTML report in your browser whenever the run is considered failed
  reporter: process.env.CI ? 'dot' : [['html', { open: 'on-failure' }]],
  use: {
    baseURL: process.env.CARDS_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  // Patient portal + fixtures can exceed 60s without slowMo; keep headroom for CI/IDE runners
  timeout: 180000,
  expect: {
    timeout: 10000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } }
  ]
});
