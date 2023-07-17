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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import io.uhndata.cards.httprequests.HttpResponse;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

public class WebhookBackupTask implements Runnable
{
    private static final String DATE_TIME_JCR_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx";

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookBackupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final String exportRunMode;

    private final LocalDateTime exportLowerBound;

    private final LocalDateTime exportUpperBound;

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final String exportRunMode)
    {
        this(resolverFactory, rrp, exportRunMode, null, null);
    }

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final String exportRunMode,
        final LocalDateTime exportLowerBound, final LocalDateTime exportUpperBound)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
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
            LOGGER.info("Executing Manual Data Export");
            doManualExport(this.exportLowerBound, null);
        } else if ("manualBetween".equals(this.exportRunMode)) {
            LOGGER.info("Executing Manual Data Export");
            doManualExport(this.exportLowerBound, this.exportUpperBound);
        }
    }

    public void doManualExport(LocalDateTime lower, LocalDateTime upper)
    {
        String requestDateStringLower = lower.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));

        String requestDateStringUpper = (upper != null)
            ? upper.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT))
            : null;

        // Notify that we are now beginning the backup
        LOGGER.info("Backup started for jcr:lastModified >= {} && jcr:lastModified < {}", lower, upper);
        postToSlack(generateBackupStatus(lower, upper, "started", ":large_yellow_circle:"));

        // Get lists of the paths to all cards:Subject and cards:Form JCR nodes
        try {
            Set<List<String>> subjectList = getUuidPathList("cards:Subject");
            Set<List<String>> formList = getUuidPathList("cards:Form");

            // Send these lists over to the backup server
            sendStringListSet(subjectList, "SubjectListBackup");
            sendStringListSet(formList, "FormListBackup");

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
        } catch (IOException e) {
            LOGGER.info("Backup failed for jcr:lastModified >= {} && jcr:lastModified < {}", lower, upper);
            postToSlack(generateBackupStatus(lower, upper, "failed", ":red_circle:"));
            return;
        }
        // Notify that the backup has finished
        LOGGER.info("Backup finished for jcr:lastModified >= {} && jcr:lastModified < {}", lower, upper);
        postToSlack(generateBackupStatus(lower, upper, "finished", ":large_green_circle:"));
    }

    /*
     * This should be scheduled to run late at night (eg. 00:00 or 00:30 or 01:30, etc...)
     * and backup all Forms and Subjects that were jcr:lastModified on the previous day.
     * For example, if this job runs on 2022-05-11 at 00:30, it will backup all Forms
     * and Subjects that were jcr:lastModified any time after and including 2022-05-10T00:00:00
     * but before 2022-05-11T00:00:00. Similarly on 2022-05-12 at 00:30, this job will back up
     * all data jcr:lastModified within [2022-05-11T00:00:00, 2022-05-12T00:00:00)
     */
    public void doNightlyExport()
    {
        LOGGER.info("Executing NightlyExport");
        LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfYesterday = startOfToday.minusDays(1);
        String requestStartString = startOfYesterday.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));
        String requestEndString = startOfToday.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));
        LOGGER.warn("Exporting data modified between [{}, {})", requestStartString, requestEndString);
        doManualExport(startOfYesterday, startOfToday);
    }

    private Set<String> getChangedFormsBounded(String requestDateStringLower, String requestDateStringUpper)
        throws IOException
    {
        return getChangedNodesBounded("cards:Form", requestDateStringLower, requestDateStringUpper);
    }

    private Set<String> getChangedSubjectsBounded(String requestDateStringLower, String requestDateStringUpper)
        throws IOException
    {
        return getChangedNodesBounded("cards:Subject", requestDateStringLower, requestDateStringUpper);
    }

    private Set<String> getChangedNodesBounded(String cardsType,
        String requestDateStringLower, String requestDateStringUpper) throws IOException
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> changedNodes = new HashSet<>();
            String query = String.format(
                "SELECT * FROM [" + cardsType + "] AS node"
                    + " WHERE node.[jcr:lastModified] >= '%s'"
                    + ((requestDateStringUpper != null) ? " AND node.[jcr:lastModified] < '%s'" : ""),
                requestDateStringLower, requestDateStringUpper
            );
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource node = results.next();
                String nodePath = node.getPath();
                changedNodes.add(nodePath);
            }
            return changedNodes;
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getChangedNodesBounded: {}", e);
            throw new IOException("LoginException in getChangedNodesBounded");
        }
    }

    private Set<List<String>> getUuidPathList(String cardsType) throws IOException
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<List<String>> uuidPaths = new HashSet<>();
            String query = "SELECT * FROM [" + cardsType + "] as n order by n.'jcr:created'";
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource resource = results.next();
                List<String> formDescription = new ArrayList<>();
                String uuidPath = resource.getPath();
                String lastModified = resource.getValueMap().get("jcr:lastModified", "");
                formDescription.add(uuidPath);
                formDescription.add(lastModified);
                uuidPaths.add(formDescription);
            }
            return uuidPaths;
        } catch (LoginException e) {
            LOGGER.warn("Get service session failure: {}", e.getMessage(), e);
            throw new IOException("LoginException in getUuidPathList");
        }
    }

    private String getFormAsJson(String formPath) throws IOException
    {
        boolean mustPopResolver = false;
        String formDataUrl = String.format("%s.deep", formPath);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            Resource formData = resolver.resolve(formDataUrl);
            return formData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            throw new IOException("getFormAsJson LoginException");
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private String getSubjectAsJson(String subjectPath) throws IOException
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            Resource subjectData = resolver.resolve(subjectPath);
            return subjectData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            throw new IOException("getSubjectAsJson LoginException");
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void sendStringListSet(Set<List<String>> set, String pathname) throws IOException
    {
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        JsonArrayBuilder jsonSetBuilder = Json.createArrayBuilder();
        Iterator<List<String>> setIterator = set.iterator();
        while (setIterator.hasNext()) {
            List<String> thisElement = setIterator.next();
            Iterator<String> listIterator = thisElement.iterator();
            JsonArrayBuilder jsonInnerSetBuilder = Json.createArrayBuilder();
            while (listIterator.hasNext()) {
                jsonInnerSetBuilder.add(listIterator.next());
            }
            jsonSetBuilder.add(jsonInnerSetBuilder);
        }
        HttpResponse webhookResp = HttpRequests.doHttpPost(
            backupWebhookUrl + "/" + pathname,
            jsonSetBuilder.build().toString(),
            "application/json"
        );
        if (webhookResp.getStatusCode() < 200 || webhookResp.getStatusCode() > 299) {
            throw new IOException("Backup server responded with a non-ok status code");
        }
    }

    private void output(String input, String filename) throws IOException
    {
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        if (backupWebhookUrl == null) {
            LOGGER.error("BACKUP_WEBHOOK_URL is undefined. Cannot run webhook backup.");
            return;
        }
        HttpResponse webhookResp = HttpRequests.doHttpPost(backupWebhookUrl + filename, input, "application/json");
        if (webhookResp.getStatusCode() < 200 || webhookResp.getStatusCode() > 299) {
            throw new IOException("Backup server responded with a non-ok status code");
        }
    }

    private void postToSlack(String msg)
    {
        final String slackNotificationsUrl = System.getenv("SLACK_BACKUP_NOTIFICATIONS_URL");
        if (slackNotificationsUrl == null) {
            LOGGER.warn(
                "SLACK_BACKUP_NOTIFICATIONS_URL environment variable is not defined. Skipping Slack notification."
            );
            return;
        }
        try {
            JsonObject slackApiReq = Json.createObjectBuilder()
                .add("text", msg)
                .build();
            HttpRequests.getPostResponse(slackNotificationsUrl, slackApiReq.toString(), "application/json");
        } catch (IOException e) {
            LOGGER.warn("Failed to send webhook backup notification to Slack");
        }
    }

    private String generateBackupStatus(LocalDateTime lower, LocalDateTime upper, String status, String emojii)
    {
        String taskUpdateMessage = emojii + " Backup " + status + " for data modified ";
        if (upper == null) {
            taskUpdateMessage += "after " + lower.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));
        } else {
            taskUpdateMessage += "between ";
            taskUpdateMessage += lower.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));
            taskUpdateMessage += " and ";
            taskUpdateMessage += upper.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(DATE_TIME_JCR_FORMAT));
        }
        taskUpdateMessage += ". " + emojii;
        return taskUpdateMessage;
    }
}
