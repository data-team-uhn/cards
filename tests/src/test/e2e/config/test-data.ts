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

/** Basic credentials for the account a freshly launched instance starts with. */
export const adminHeaders = (): Record<string, string> => ({
  Authorization: `Basic ${Buffer.from('admin:admin').toString('base64')}`,
});

/**
 * Content that exists on `test_tar` and must NOT exist on `core_tar` — the difference between the two
 * aggregates, expressed as things you can fetch.
 *
 * Shared by both suites on purpose, asserted present in one and absent in the other. A one-sided check
 * proves much less than it appears to: "the test data is here" passes just as happily against an instance
 * that is carrying test data because both suites were pointed at the same one. Each entry is verified in
 * both directions, so that mistake fails loudly instead of reading as success.
 *
 * The four cover the feature set's distinct halves — sample questionnaires, a sample subject, a
 * questionnaire that only exists because an optional question type ships with it, and an extension point
 * from a whole extra application module — so a partial aggregate cannot pass either.
 */
export const TEST_DATA_ONLY_PATHS: readonly { path: string; description: string }[] = [
  {
    path: '/Questionnaires/DateFormatsTest.json',
    description: 'the sample questionnaires the test-forms module installs',
  },
  {
    path: '/Subjects/SamplePatient.json',
    description: 'the sample subject the test-forms module installs',
  },
  {
    path: '/Questionnaires/DicomTest.json',
    description: 'the questionnaire exercising the optional DICOM question type',
  },
  {
    path: '/apps/cards/ExtensionPoints/PatientPortalFooter.json',
    description: "the patient portal module's extension points",
  },
];
