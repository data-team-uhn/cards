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

import { activeInstances, type ActiveInstance } from "./instances";

/**
 * How long to wait for an instance to become ready. Generous, because a first boot has to install every
 * bundle and run repoinit; a warm one is far quicker.
 */
const READY_TIMEOUT_MS = 300_000;

const POLL_INTERVAL_MS = 1_000;

/** The credentials the launched instances start with. */
const AUTHORIZATION = `Basic ${Buffer.from("admin:admin").toString("base64")}`;

/** Health check outcomes that mean the instance is not usable yet. */
const BLOCKING_STATUSES = ["CRITICAL", "HEALTH_CHECK_ERROR", "TEMPORARILY_UNAVAILABLE"];

/**
 * Which health checks have to be satisfied.
 *
 * `cards` is the platform's own set. `bundles` adds "Bundles Started" and, more importantly, "Bundle
 * Content Loaded" — the platform's own answer to whether every bundle's initial content has finished
 * installing, which is precisely the race a suite hits when it asserts on content a module ships. Asking
 * the platform beats naming paths by hand: it covers every bundle rather than the one somebody thought to
 * list.
 */
const HEALTH_TAGS = "cards,bundles";

/**
 * The number of checks is itself a readiness signal. The health servlet answers as soon as it is up,
 * while the checks are still registering, so an early call returns a short all-OK list that means nothing
 * — one observed run saw a single check pass while the one that later reported CRITICAL had not
 * registered yet. Waiting for the full set closes that window.
 */
const EXPECTED_CHECK_COUNT = 6;

/**
 * Whether an instance is ready to be tested.
 *
 * The launcher plugin only waits for the OSGi framework to come up, which happens well before the
 * repository is initialized and the application is servable. So this asks for two things: that the
 * platform's own health checks report nothing broken, and that the sign-in page actually renders. Neither
 * alone is enough -- the health endpoint answers before the frontend is wired up, and a page can be
 * served while the repository behind it is still being initialized.
 *
 * A WARN is deliberately not blocking. It means something is worth looking at, not that the instance is
 * unusable, and treating it as fatal here would turn a passing suite into a startup timeout.
 */
const isReady = async (instance: ActiveInstance): Promise<boolean> => {
  try {
    const health = await fetch(`${instance.baseURL}/system/health.json?tags=${HEALTH_TAGS}`, {
      headers: { Authorization: AUTHORIZATION },
    });
    if (!health.ok) {
      return false;
    }
    const body = (await health.json()) as { results?: { status?: string }[] };
    const results = body.results ?? [];
    if (results.length < EXPECTED_CHECK_COUNT) {
      return false;
    }
    if (results.some(result => BLOCKING_STATUSES.includes(result.status ?? ""))) {
      return false;
    }
    const loginPage = await fetch(`${instance.baseURL}/login`);
    if (!loginPage.ok) {
      return false;
    }
    // Whatever content this suite's pages depend on. Anonymously, exactly as the browser will read it --
    // see Instance.readyPaths for why checking as admin here would defeat the purpose.
    const content = await Promise.all(
      (instance.readyPaths ?? []).map(path => fetch(`${instance.baseURL}${path}`)),
    );
    return content.every(response => response.ok);
  } catch {
    // Connection refused while the HTTP service is still coming up
    return false;
  }
};

/**
 * Why an instance is still not ready, in the words of whatever is unhappy.
 *
 * Only ever called once, on the way to throwing. Worth the extra request: "health checks not passing"
 * sends whoever reads it off to launch an instance by hand to find out which one, and the answer is
 * sitting in a response the setup already knows how to fetch.
 */
const describeNotReady = async (instance: ActiveInstance): Promise<string> => {
  try {
    const health = await fetch(`${instance.baseURL}/system/health.json?tags=${HEALTH_TAGS}`, {
      headers: { Authorization: AUTHORIZATION },
    });
    if (!health.ok) {
      return `the health endpoint answered ${health.status}`;
    }
    const body = (await health.json()) as {
      results?: { name?: string; status?: string; messages?: { message?: string }[] }[];
    };
    const results = body.results ?? [];
    const blocking = results.filter(result => BLOCKING_STATUSES.includes(result.status ?? ""));
    if (blocking.length > 0) {
      return blocking
        .map(result => {
          const detail = (result.messages ?? []).map(message => message.message).filter(Boolean).join("; ");
          return `${result.name}: ${result.status}${detail ? ` (${detail})` : ""}`;
        })
        .join(", ");
    }
    if (results.length < EXPECTED_CHECK_COUNT) {
      return `only ${results.length} of ${EXPECTED_CHECK_COUNT} health checks have registered`;
    }
    const unreadable = await Promise.all(
      (instance.readyPaths ?? []).map(async path => {
        const response = await fetch(`${instance.baseURL}${path}`);
        return response.ok ? null : `${path} answered ${response.status} anonymously`;
      }),
    );
    const paths = unreadable.filter(Boolean);
    if (paths.length > 0) {
      return paths.join(", ");
    }
    return "the sign-in page is not being served";
  } catch (error) {
    return `no response (${String(error)})`;
  }
};

const waitFor = async (instance: ActiveInstance): Promise<void> => {
  const deadline = Date.now() + READY_TIMEOUT_MS;
  let lastError = "no response";
  while (Date.now() < deadline) {
    if (await isReady(instance)) {
      return;
    }
    lastError = await describeNotReady(instance);
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
  }
  throw new Error(
    `${instance.description} at ${instance.baseURL} was not ready within `
      + `${READY_TIMEOUT_MS / 1000}s (${lastError}).`,
  );
};

export default async function globalSetup(): Promise<void> {
  const instances = activeInstances();
  if (instances.length === 0) {
    throw new Error(
      "No instances to test. Maven passes their URLs in; to run by hand, set e.g. CARDS_CORE_URL.",
    );
  }
  // Concurrently, since the instances boot independently and waiting for them in turn would add up
  await Promise.all(
    instances.map(async instance => {
      await waitFor(instance);
      console.log(`ready: ${instance.description} at ${instance.baseURL}`);
    }),
  );
}
