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

package io.uhndata.cards.llm;

/**
 * Service that records {@link LLMInteraction}s to an external observability backend (for example a LangFuse
 * instance). Implementations must never block the LLM call path or throw: {@link #logInteraction} should hand
 * the work off asynchronously and swallow any delivery failure. The router consults {@link #isEnabled()} before
 * gathering the data for an interaction so that the overhead is avoided entirely when logging is turned off.
 *
 * @version $Id$
 */
public interface LLMInteractionLogger
{
    /**
     * Whether interaction logging is currently active. When this returns {@code false}, callers should skip
     * building and submitting interactions.
     *
     * @return {@code true} if interactions will be recorded, {@code false} otherwise
     */
    boolean isEnabled();

    /**
     * Record a completed (or failed) LLM interaction. Implementations must return promptly and must not throw;
     * delivery happens asynchronously and any failure is logged rather than propagated.
     *
     * @param interaction the interaction to record; ignored if {@code null}
     */
    void logInteraction(LLMInteraction interaction);
}
