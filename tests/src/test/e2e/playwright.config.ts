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

import { defineConfig, devices } from "@playwright/test";

import { activeInstances } from "./support/instances";

const isCI = Boolean(process.env.CI);

/**
 * The device profile to drive each browser engine with. Also the set of names `playwright.browsers`
 * accepts, so that asking for one that cannot be run says so instead of quietly running Chromium.
 */
const DEVICE_FOR_BROWSER: Record<string, string> = {
  chromium: "Desktop Chrome",
  firefox: "Desktop Firefox",
  webkit: "Desktop Safari",
};

/**
 * The engines to run on, from the same `playwright.browsers` property that decides which ones the build
 * downloads -- so the two can never disagree, which is exactly what happens when a browser is installed
 * and then never used.
 */
const browsers = (): string[] => {
  const requested = (process.env.PLAYWRIGHT_BROWSERS ?? "chromium").trim().split(/\s+/).filter(Boolean);
  const unknown = requested.filter(browser => !(browser in DEVICE_FOR_BROWSER));
  if (unknown.length > 0) {
    throw new Error(
      `Unknown browser(s): ${unknown.join(", ")}. Known: ${Object.keys(DEVICE_FOR_BROWSER).join(", ")}.`,
    );
  }
  return requested.length > 0 ? requested : ["chromium"];
};

/**
 * One Playwright project per launched instance per browser, each pointed at its own instance and reading
 * its specs from its own directory. Anything shared -- page objects, flows, fixtures -- lives outside
 * those directories and is imported.
 *
 * A suite whose instance was not launched simply produces no project, so `-Dit.core.skip=true` needs no
 * matching change here.
 *
 * Projects are named `<suite>-<browser>` even when only one browser is being run. The suffix is not
 * decoration: a name that appeared only once a second browser was configured would silently invalidate
 * every `--project=` already written down, and a report that says which engine a failure came from is
 * worth more than four saved keystrokes.
 */
export default defineConfig({
  testDir: "./specs",
  outputDir: "../../../target/e2e-results",
  // One worker, and no parallelism within a file: these tests sign in and out of a single shared
  // instance, and nothing isolates one test's session from another's.
  fullyParallel: false,
  workers: 1,
  // A stray `test.only` must not silently shrink the suite on CI
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "../../../target/e2e-report", open: "never" }],
    // Machine-readable, because the Maven build defers the failure: the test run must not stop the build
    // before the instances have been shut down, so `check-results.mjs` reads this at verify time instead.
    ["json", { outputFile: "../../../target/e2e-results.json" }],
  ],
  globalSetup: "./support/global-setup.ts",
  // Generous, because a single test may walk through several full page loads of a React application
  // served by a JVM that has just started.
  timeout: 180_000,
  // Playwright's 5s default is tight for these pages: each one boots React and then fetches its
  // extensions and content before anything is assertable. Assertions still resolve as soon as the
  // condition holds, so a passing run is no slower for it.
  expect: { timeout: 15_000 },
  use: {
    // Kept on retry and on failure: a trace is the difference between diagnosing a CI-only failure in
    // minutes and not at all
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    ignoreHTTPSErrors: true,
  },
  projects: activeInstances().flatMap(instance =>
    browsers().map(browser => ({
      name: `${instance.name}-${browser}`,
      testDir: `./specs/${instance.name}`,
      use: { ...devices[DEVICE_FOR_BROWSER[browser]], baseURL: instance.baseURL },
    })),
  ),
});
