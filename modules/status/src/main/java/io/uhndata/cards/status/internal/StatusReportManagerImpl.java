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

package io.uhndata.cards.status.internal;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.uhndata.cards.status.api.StatusReportManager;
import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

@Component
public class StatusReportManagerImpl implements StatusReportManager
{
    /** A list of all available reporters. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<StatusReporter> reporters;

    @Override
    public List<StatusReport> getReports(final boolean unprivileged, final StatusReport.Status level,
        final Set<String> tags)
    {
        return this.reporters.stream()
            .filter(r -> tags == null || tags.isEmpty() || !Collections.disjoint(r.getTags(), tags))
            .map(r -> r.report(unprivileged))
            .filter(Objects::nonNull)
            .filter(r -> r.getStatus().compareTo(level) >= 0)
            .toList();
    }
}
