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
 * The instances the suites run against.
 *
 * Maven launches one instance per suite and passes its URL in through the environment, so nothing here
 * hard-codes a port. Running against an instance you started yourself is the same thing by hand:
 *
 *   CARDS_CORE_URL=http://localhost:8080 npx playwright test --project=core
 */
export interface Instance {
  /** The Playwright project name, which is also the directory its specs live in. */
  readonly name: string;
  /** Where the instance is listening, or undefined when this suite is not being run. */
  readonly baseURL: string | undefined;
  /** What this instance is, for the message printed when it never becomes ready. */
  readonly description: string;
  /**
   * Content this suite's pages depend on, which must be readable **anonymously** before the suite may
   * run.
   *
   * "Ready" depends on what a suite needs. A bundle's initial content, and the permissions that expose
   * it, land a little after the repository becomes servable -- and the frontend fetches this content once
   * on mount without ever retrying. A page that loads too early therefore renders permanently without it,
   * and no amount of assertion retrying will recover: the fetch already happened and failed.
   *
   * Probed without credentials on purpose. Checking as admin would pass while the anonymous read the
   * browser actually performs still fails, which is precisely the window that makes such a suite flaky.
   */
  readonly readyPaths?: readonly string[];
}

const enabled = (skipFlag: string | undefined, url: string | undefined): string | undefined =>
  skipFlag === "true" ? undefined : url;

export const INSTANCES: readonly Instance[] = [
  {
    name: "core",
    baseURL: enabled(process.env.CARDS_CORE_SKIP, process.env.CARDS_CORE_URL),
    description: "the core distribution (core_tar)",
  },
];

/**
 * An instance that is being run, and therefore has a URL. `activeInstances` is the only way to obtain
 * one, so whatever holds an `ActiveInstance` can address it without re-checking that it is there.
 */
export type ActiveInstance = Instance & { baseURL: string };

/** The instances actually being exercised on this run. */
export const activeInstances = (): ActiveInstance[] =>
  INSTANCES.filter((instance): instance is ActiveInstance => Boolean(instance.baseURL));
