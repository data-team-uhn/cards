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

package io.uhndata.cards.status.api;

import java.util.List;
import java.util.Set;

import io.uhndata.cards.status.spi.StatusReport;

/**
 * @version $Id$
 * @since 0.9.38
 */
public interface StatusReportManager
{
    /**
     * Get all the non-debug status reports.
     *
     * @param unprivileged Whether the body of the report should not include sensitive information, since the report
     *            will be posted into an unprivileged/unsecure location. {@code false} means that the report may include
     *            confidential information, {@code true} means it should not.
     * @return a list of status reports
     */
    default List<StatusReport> getReports(boolean unprivileged)
    {
        return getReports(unprivileged, StatusReport.Status.INFO, null);
    }

    /**
     * @param unprivileged Whether the body of the report should not include sensitive information, since the report
     *            will be posted into an unprivileged/unsecure location. {@code false} means that the report may include
     *            confidential information, {@code true} means it should not.
     * @param level the target level, only include reports that are at this or a higher level of importance
     * @param tags an optional set of tags of reporters to include, for example only {@code problems} or only
     *            {@code activity}; {@code null} or an empty set includes all reporters
     * @return a list of status reports
     */
    List<StatusReport> getReports(boolean unprivileged, StatusReport.Status level, Set<String> tags);
}
