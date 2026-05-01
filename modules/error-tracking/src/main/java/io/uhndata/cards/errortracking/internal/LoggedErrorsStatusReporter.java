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

package io.uhndata.cards.errortracking.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.status.spi.StatusReport;
import io.uhndata.cards.status.spi.StatusReporter;

@Component(immediate = true)
public class LoggedErrorsStatusReporter implements StatusReporter
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggedErrorsStatusReporter.class);

    /** Provides access to the repository. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Override
    public String getName()
    {
        return "Logged errors";
    }

    @Override
    public StatusReport report(boolean unprivileged)
    {
        LOGGER.debug("Gathering errors for the status report");
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            // Get all the error stack traces under /LoggedEvents/
            List<String> errorStackTraces = getLoggedEvents(resolver);
            LOGGER.debug("Found {} logged errors", errorStackTraces.size());

            if (errorStackTraces.size() > 0 && unprivileged) {
                return new StatusReport("There are " + errorStackTraces.size() + " errors logged",
                    StatusReport.Status.ERROR, "Error content is hidden while not logged in");
            }
            String statusBody = generateStackTraceMessagePart(errorStackTraces);

            if (statusBody.length() > 0) {
                return new StatusReport("The following errors are logged:", StatusReport.Status.ERROR, statusBody);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to report logged errors: {}", e.getMessage(), e);
            return new StatusReport("*ERROR*: Could not report logged errors", StatusReport.Status.ERROR,
                e.getMessage());
        }
        return new StatusReport("No errors are logged", StatusReport.Status.DEBUG, "");
    }

    private List<String> getLoggedEvents(ResourceResolver resolver)
    {
        // Get all the nt:file nodes under /LoggedEvents/
        Iterator<Resource> loggedEventsIter;
        loggedEventsIter = resolver.findResources(
            "SELECT n.* FROM [nt:file] AS n WHERE isdescendantnode(n, '/LoggedEvents')",
            "JCR-SQL2");

        List<String> errorStackTraces = new ArrayList<>();
        while (loggedEventsIter.hasNext()) {
            Resource thisResource = loggedEventsIter.next();
            Resource jcrContentResource = thisResource.getChild("jcr:content");
            if (jcrContentResource == null) {
                continue;
            }
            String thisStackTrace = jcrContentResource.getValueMap().get("jcr:data", "");
            errorStackTraces.add(thisStackTrace);
        }
        return errorStackTraces;
    }

    private String generateStackTraceMessagePart(List<String> errorStackTraces)
    {
        StringBuilder result = new StringBuilder();

        for (String element : errorStackTraces) {
            result.append("```\n" + element + "\n```\n\n");
        }
        return result.toString();
    }

    @Override
    public Set<String> getTags()
    {
        return Set.of("problems", "errors");
    }
}
