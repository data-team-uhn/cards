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

package io.uhndata.cards.status.spi;

import java.util.Set;

/**
 * Service interface for producing status reports. When it's time to produce a status report, each implementation's
 * {@link #report} will be invoked.
 *
 * @version $Id$
 * @since 0.9.38
 */
public interface StatusReporter
{
    /**
     * The name of this reporter, used to identify it.
     *
     * @return a simple string
     */
    String getName();

    /**
     * The tags used to categorize this reporter, used to enable/disable it for specific jobs.
     *
     * @return a simple string
     */
    Set<String> getTags();

    /**
     * Prepare and return a status report.
     *
     * @param unprivileged Whether the body of the report should not include sensitive information, since the report
     *            will be posted into an unprivileged/unsecure location. {@code false} means that the report may include
     *            confidential information, {@code true} means it should not.
     * @return a status report, or {@code null} if there is nothing to report
     */
    StatusReport report(boolean unprivileged);
}
