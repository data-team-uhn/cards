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

/*
 * Fails the build if any test failed.
 *
 * The test run itself is not allowed to fail the build, because a failure there would skip
 * post-integration-test and leave the instances running -- the launcher plugin's shutdown hook kills the
 * launcher script but not the JVM underneath it, so every failed run would orphan a full Sling instance
 * and hold its port. So the run is deferred, exactly as maven-failsafe-plugin defers its own, and the
 * verdict is read back here once the instances have been stopped.
 *
 * Plain .mjs rather than TypeScript: this runs under bare Node, outside Playwright's transpiler.
 */

import { readFileSync } from "node:fs";

const REPORT = new URL("../../../../target/e2e-results.json", import.meta.url);

const collectSpecs = suite =>
  (suite.suites ?? []).flatMap(collectSpecs).concat(suite.specs ?? []);

let report;
try {
  report = JSON.parse(readFileSync(REPORT, "utf8"));
} catch (error) {
  // No report at all means the run never got far enough to write one, which is itself a failure
  console.error(`Could not read the Playwright report at ${REPORT.pathname}: ${error.message}`);
  process.exit(1);
}

const specs = (report.suites ?? []).flatMap(collectSpecs);
const failures = specs.filter(spec => !spec.ok);

if (failures.length > 0) {
  console.error(`\n${failures.length} integration test(s) failed:\n`);
  for (const spec of failures) {
    const project = spec.tests?.[0]?.projectName ?? "unknown";
    console.error(`  [${project}] ${spec.title}  (${spec.file}:${spec.line})`);
  }
  console.error("\nThe HTML report, with a trace for each failure, is in tests/target/e2e-report.");
  console.error("Open it with: cd tests/src/test/e2e && yarn report\n");
  process.exit(1);
}

if (specs.length === 0) {
  // A suite that ran nothing must not read as success
  console.error("The Playwright report contains no tests at all.");
  process.exit(1);
}

console.log(`All ${specs.length} integration tests passed.`);
