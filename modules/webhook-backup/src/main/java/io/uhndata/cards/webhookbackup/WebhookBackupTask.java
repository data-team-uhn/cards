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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.httprequests.HttpRequests;

public class WebhookBackupTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookBackupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;
    private final String exportRunMode;
    private final LocalDateTime exportLowerBound;
    private final LocalDateTime exportUpperBound;

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final String exportRunMode)
    {
        this.resolverFactory = resolverFactory;
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = null;
        this.exportUpperBound = null;
    }

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final String exportRunMode,
        final LocalDateTime exportLowerBound, final LocalDateTime exportUpperBound)
    {
        this.resolverFactory = resolverFactory;
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = exportLowerBound;
        this.exportUpperBound = exportUpperBound;
    }

    @Override
    public void run()
    {
        if ("nightly".equals(this.exportRunMode) || "manualToday".equals(this.exportRunMode)) {
            doNightlyExport();
        } else if ("manualAfter".equals(this.exportRunMode)) {
            doManualExport(this.exportLowerBound, null);
        } else if ("manualBetween".equals(this.exportRunMode)) {
            doManualExport(this.exportLowerBound, this.exportUpperBound);
        }
    }

    public void doManualExport(LocalDateTime lower, LocalDateTime upper)
    {
        LOGGER.info("Executing ManualExport");
        String requestDateStringLower = lower.toString();
        String requestDateStringUpper = (upper != null)
            ? upper.toString()
            : null;

        // Get lists of the paths to all cards:Subject and cards:Form JCR nodes
        Set<String> subjectList = getUuidPathList("cards:Subject");
        Set<String> formList = getUuidPathList("cards:Form");

        // Send these lists over to the backup server
        sendStringSet(subjectList, "SubjectListBackup");
        sendStringSet(formList, "FormListBackup");

        // Iterate through all Form nodes that were changed within the given timeframe and back them up
        Set<String> changedFormList = getChangedFormsBounded(requestDateStringLower, requestDateStringUpper);
        for (String formPath : changedFormList) {
            String formData = getFormAsJson(formPath);
            this.output(formData, "/FormBackup" + formPath);
        }

        // Iterate through all Subject nodes that were changed within the given timeframe and back them up
        Set<String> changedSubjectList = getChangedSubjectsBounded(requestDateStringLower, requestDateStringUpper);
        for (String subjectPath : changedSubjectList) {
            String subjectData = getSubjectAsJson(subjectPath);
            this.output(subjectData, "/SubjectBackup" + subjectPath);
        }
    }

    public void doNightlyExport()
    {
        LOGGER.info("Executing NightlyExport");
        LocalDateTime today = LocalDateTime.now();
        String fileDateString = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String requestDateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // TODO: Implement me
    }

    private Set<String> getChangedFormsBounded(String requestDateStringLower, String requestDateStringUpper)
    {
        return getChangedNodesBounded("cards:Form", requestDateStringLower, requestDateStringUpper);
    }

    private Set<String> getChangedSubjectsBounded(String requestDateStringLower, String requestDateStringUpper)
    {
        return getChangedNodesBounded("cards:Subject", requestDateStringLower, requestDateStringUpper);
    }

    private Set<String> getChangedNodesBounded(String cardsType,
        String requestDateStringLower, String requestDateStringUpper)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> changedForms = new HashSet<>();
            String query = String.format(
                "SELECT * FROM [" + cardsType + "] AS form"
                    + " WHERE form.[jcr:lastModified] >= '%s'"
                    + ((requestDateStringUpper != null) ? " AND form.[jcr:lastModified] < '%s'" : ""),
                requestDateStringLower, requestDateStringUpper
            );
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource form = results.next();
                String formPath = form.getPath();
                changedForms.add(formPath);
            }
            return changedForms;
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormsChangedBounded: {}", e);
        }
        return Collections.emptySet();
    }

    private Set<String> getUuidPathList(String cardsType)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> uuidPaths = new HashSet<>();
            String query = "SELECT * FROM [" + cardsType + "] as n order by n.'jcr:created'";
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource resource = results.next();
                String uuidPath = resource.getPath();
                uuidPaths.add(uuidPath);
            }
            return uuidPaths;
        } catch (LoginException e) {
            LOGGER.warn("Get service session failure: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private String getFormAsJson(String formPath)
    {
        String formDataUrl = String.format("%s.data.deep", formPath);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource formData = resolver.resolve(formDataUrl);
            return formData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            return null;
        }
    }

    private String getSubjectAsJson(String subjectPath)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectPath);
            return subjectData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            return null;
        }
    }

    private void sendStringSet(Set<String> set, String pathname)
    {
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        JsonArrayBuilder jsonSetBuilder = Json.createArrayBuilder();
        Iterator<String> setIterator = set.iterator();
        while (setIterator.hasNext()) {
            jsonSetBuilder.add(setIterator.next());
        }
        try {
            HttpRequests.getPostResponse(
                backupWebhookUrl + "/" + pathname,
                jsonSetBuilder.build().toString(),
                "application/json"
            );
        } catch (IOException e) {
            LOGGER.warn("IOException while in sendStringSet: {}", e);
        }
    }

    private void output(String input, String filename)
    {
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        try {
            HttpRequests.getPostResponse(backupWebhookUrl + filename, input, "application/json");
        } catch (IOException e) {
            LOGGER.warn("Backup failed due to {}", e);
        }
    }
}
