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
package io.uhndata.cards.webhookbackup;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/SubjectsHomepage" },
    selectors = { "webhookbackup" })
public class WebhookBackupEndpoint extends SlingSafeMethodsServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookBackupEndpoint.class);

    private static final long serialVersionUID = -6489305069446929017L;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final Writer out = response.getWriter();

        // Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !"admin".equals(remoteUser)) {
            // admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }

        final LocalDateTime dateLowerBound;
        try {
            dateLowerBound = this.strToDateTime(request.getParameter("dateLowerBound"));
        } catch (DateTimeParseException e) {
            // Wasn't able to parse the passed DateTime
            response.setStatus(400);
            out.write("Error: Invalid date/time specified for dateLowerBound");
            return;
        }

        final LocalDateTime dateUpperBound;
        try {
            dateUpperBound = this.strToDateTime(request.getParameter("dateUpperBound"));
        } catch (DateTimeParseException e) {
            // Wasn't able to parse the passed DateTime
            response.setStatus(400);
            out.write("Error: Invalid date/time specified for dateUpperBound");
            return;
        }

        final String exportRunMode = (dateLowerBound != null && dateUpperBound != null)
            ? "manualBetween"
            : (dateLowerBound != null && dateUpperBound == null) ? "manualAfter" : "manualToday";

        final Runnable exportJob = ("manualToday".equals(exportRunMode))
            ? new WebhookBackupTask(this.resolverFactory, this.rrp, exportRunMode)
            : new WebhookBackupTask(this.resolverFactory, this.rrp, exportRunMode, dateLowerBound, dateUpperBound);
        final Thread thread = new Thread(exportJob);
        thread.start();
        out.write("Webhook Backup export started");
    }

    private LocalDateTime strToDateTime(final String date) throws DateTimeParseException
    {
        LOGGER.warn("strToDateTime attempting to parse: {}", date);
        if (date == null) {
            return null;
        }
        return LocalDateTime.parse(date);
    }
}
