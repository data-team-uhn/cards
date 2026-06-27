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

package io.uhndata.cards.healthcheck.internal;

import java.util.List;
import java.util.Set;

import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

@Component(immediate = true)
public class HealthCheckStatusReporter implements StatusReporter
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckStatusReporter.class);

    private static final String TITLE = "Health Check";

    /** Provides access to the repository. */
    @Reference
    private HealthCheckExecutor hc;

    @Override
    public String getName()
    {
        return "Health Check";
    }

    @Override
    public StatusReport report(boolean unprivileged)
    {
        LOGGER.debug("Gathering errors for the Slack notification");
        List<HealthCheckExecutionResult> failedChecks = this.hc
            .execute(HealthCheckSelector.empty().withTags("*"),
                new HealthCheckExecutionOptions().setOverrideGlobalTimeout(10_000).setForceInstantExecution(true))
            .stream()
            .filter(r -> !r.getHealthCheckResult().isOk())
            .toList();
        if (failedChecks.isEmpty()) {
            return new StatusReport(TITLE, StatusReport.Status.SUCCESS, "All is good!");
        }
        LOGGER.warn("There are {} failed checks! {}", failedChecks.size(),
            failedChecks.stream().map(r -> r.getHealthCheckMetadata().getName()).toList());
        StringBuilder text = new StringBuilder("There are " + failedChecks.size() + " failed checks");
        if (!unprivileged) {
            text.append("\n\n");
            failedChecks.forEach(error -> text.append(error.getHealthCheckMetadata().getName()).append("\n"));
        }
        Status status = failedChecks.stream().map(r -> r.getHealthCheckResult().getStatus()).reduce(Status.OK,
            (o, n) -> o.compareTo(n) < 0 ? n : o);
        return new StatusReport(TITLE,
            status.ordinal() >= Status.TEMPORARILY_UNAVAILABLE.ordinal() ? StatusReport.Status.ERROR
                : StatusReport.Status.WARNING,
            text.toString());
    }

    @Override
    public Set<String> getTags()
    {
        return Set.of("problems", "healthcheck");
    }
}
