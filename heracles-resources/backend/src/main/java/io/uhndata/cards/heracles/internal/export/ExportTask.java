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

package io.uhndata.cards.heracles.internal.export;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.uhndata.cards.errortracking.ErrorLogger;
import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

public class ExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    private static final String DOT = "\\.";

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final String exportRunMode;

    private final LocalDate exportLowerBound;

    private final LocalDate exportUpperBound;

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final String exportRunMode)
    {
        this(resolverFactory, rrp, exportRunMode, null, null);
    }

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final String exportRunMode,
        final LocalDate exportLowerBound, final LocalDate exportUpperBound)
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
        try {
            if ("nightly".equals(this.exportRunMode) || "manualToday".equals(this.exportRunMode)) {
                doNightlyExport();
            } else if ("manualAfter".equals(this.exportRunMode)) {
                doManualExport(this.exportLowerBound, null);
            } else if ("manualBetween".equals(this.exportRunMode)) {
                doManualExport(this.exportLowerBound, this.exportUpperBound);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to perform the nightly export", e.getMessage(), e);

            // Store the stack trace of this failure
            ErrorLogger.logError(e);

            // Increment the count of S3ExportFailures
            Metrics.increment(this.resolverFactory, "S3ExportFailures", 1);
        }
    }

    public void doManualExport(LocalDate lower, LocalDate upper) throws LoginException
    {
        LOGGER.info("Executing ManualExport");
        String fileDateString = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String requestDateStringLower =
            lower.atStartOfDay(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx"));
        String requestDateStringUpper = (upper != null)
            ? upper.atStartOfDay(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx"))
            : null;

        boolean mustPopResolver = false;
        try (ResourceResolver resolver =
            this.resolverFactory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "S3Export"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            Set<SubjectIdentifier> changedSubjects =
                this.getChangedSubjects(requestDateStringLower, requestDateStringUpper, resolver);

            for (SubjectIdentifier identifier : changedSubjects) {
                SubjectContents subjectContents =
                    getSubjectContents(identifier.getPath(), requestDateStringLower, requestDateStringUpper, resolver);
                if (subjectContents != null) {
                    String filename = String.format(
                        "%s_formData_%s.json",
                        cleanString(identifier.getParticipantId()),
                        fileDateString);
                    this.output(subjectContents, filename);
                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    public void doNightlyExport() throws LoginException
    {
        LOGGER.info("Executing NightlyExport");
        ZonedDateTime yesterday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(1);
        String fileDateString = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String startDateString = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx"));
        String endDateString =
            yesterday.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx"));
        boolean mustPopResolver = false;
        try (ResourceResolver resolver =
            this.resolverFactory.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "S3Export"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            Set<SubjectIdentifier> changedSubjects = this.getChangedSubjects(startDateString, endDateString, resolver);

            for (SubjectIdentifier identifier : changedSubjects) {
                SubjectContents subjectContents =
                    getSubjectContents(identifier.getPath(), startDateString, endDateString, resolver);
                if (subjectContents != null) {
                    String filename = String.format(
                        "%s_formData_%s.json",
                        cleanString(identifier.getParticipantId()),
                        fileDateString);
                    this.output(subjectContents, filename);
                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private String cleanString(String input)
    {
        return input.replaceAll("[^A-Za-z0-9]", "");
    }

    private String escapeForDataUrl(String input)
    {
        return input.replaceAll(DOT, Matcher.quoteReplacement(DOT));
    }

    private static final class SubjectIdentifier
    {
        private String path;

        private String participantId;

        SubjectIdentifier(String path, String participantId)
        {
            this.path = path;
            this.participantId = participantId;
        }

        public String getPath()
        {
            return this.path;
        }

        public String getParticipantId()
        {
            return this.participantId;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.path.hashCode()) + Objects.hashCode(this.participantId.hashCode());
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            SubjectIdentifier other = (SubjectIdentifier) obj;
            return Objects.equals(this.path, other.getPath())
                && Objects.equals(this.participantId, other.getParticipantId());
        }

        @Override
        public String toString()
        {
            return String.format("{path:\"%s\",participantId:\"%s\"}", this.path, this.participantId);
        }
    }

    private static final class SubjectContents
    {
        private final String data;

        private final JsonObject summary;

        private final String url;

        SubjectContents(final String data, final JsonObject summary, final String url)
        {
            this.data = data;
            this.summary = summary;
            this.url = url;
        }

        public String getData()
        {
            return this.data;
        }

        public List<String> getSummary()
        {
            return this.summary.values().stream()
                .filter(v -> v.getValueType() == ValueType.ARRAY)
                .map(JsonValue::asJsonArray)
                .flatMap(JsonArray::stream)
                .filter(v -> v.getValueType() == ValueType.OBJECT)
                .map(JsonValue::asJsonObject)
                .filter(v -> v.containsKey("@path"))
                .map(v -> v.getString("@path"))
                .collect(Collectors.toList());
        }

        public String getUrl()
        {
            return this.url;
        }
    }

    private Set<SubjectIdentifier> getChangedSubjects(String requestDateStringLower,
        String requestDateStringUpper, final ResourceResolver resolver) throws LoginException
    {
        Set<SubjectIdentifier> subjects = new HashSet<>();
        String query = String.format(
            "SELECT subject.* FROM [cards:Form] AS form INNER JOIN [cards:Subject] AS subject"
                + " ON form.'subject'=subject.[jcr:uuid]"
                + " WHERE form.[jcr:lastModified] >= '%s'"
                + (requestDateStringUpper != null ? " AND form.[jcr:lastModified] < '%s'" : "")
                + " AND NOT form.[statusFlags] = 'INCOMPLETE'",
            requestDateStringLower, requestDateStringUpper);

        Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
        while (results.hasNext()) {
            Resource subject = results.next();
            String path = subject.getPath();
            String participantId = subject.getValueMap().get("identifier", String.class);
            subjects.add(new SubjectIdentifier(path, participantId));
        }
        return subjects;
    }

    private SubjectContents getSubjectContents(String path, String requestDateStringLower,
        String requestDateStringUpper, final ResourceResolver resolver) throws LoginException
    {
        String subjectDataUrl = String.format("%s.data.deep.bare.-labels.-identify.relativeDates.nolinks"
            + ".dataFilter:modifiedAfter=%s" + (requestDateStringUpper != null ? ".dataFilter:modifiedBefore=%s" : "")
            + ".dataFilter:statusNot=INCOMPLETE",
            path, escapeForDataUrl(requestDateStringLower),
            requestDateStringUpper != null ? escapeForDataUrl(requestDateStringUpper) : "");

        String identifiedSubjectDataUrl = String.format("%s.data.identify.-properties.-dereference"
            + ".dataFilter:modifiedAfter=%s" + (requestDateStringUpper != null ? ".dataFilter:modifiedBefore=%s" : "")
            + ".dataFilter:statusNot=INCOMPLETE",
            path, escapeForDataUrl(requestDateStringLower),
            requestDateStringUpper != null ? escapeForDataUrl(requestDateStringUpper) : "");
        Resource subjectData = resolver.resolve(subjectDataUrl);
        Resource identifiedSubjectData = resolver.resolve(identifiedSubjectDataUrl);
        return new SubjectContents(subjectData.adaptTo(JsonObject.class).toString(),
            identifiedSubjectData.adaptTo(JsonObject.class), subjectDataUrl);
    }

    private void output(SubjectContents input, String filename)
    {
        final String s3EndpointUrl = System.getenv("S3_ENDPOINT_URL");
        final String s3EndpointRegion = System.getenv("S3_ENDPOINT_REGION");
        final String s3BucketName = System.getenv("S3_BUCKET_NAME");
        final String awsKey = System.getenv("AWS_KEY");
        final String awsSecret = System.getenv("AWS_SECRET");
        final EndpointConfiguration endpointConfig =
            new EndpointConfiguration(s3EndpointUrl, s3EndpointRegion);
        final AWSCredentials credentials = new BasicAWSCredentials(awsKey, awsSecret);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(endpointConfig)
            .withPathStyleAccessEnabled(true)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
        try {
            s3.putObject(s3BucketName, filename, input.getData());
            input.getSummary().forEach(form -> {
                LOGGER.info("Exported {}", form);
                Metrics.increment(this.resolverFactory, "S3ExportedForms", 1);
            });
            LOGGER.info("Exported {} to {}", input.getUrl(), filename);
            Metrics.increment(this.resolverFactory, "S3ExportedSubjects", 1);
        } catch (Exception e) {
            throw e;
        }
    }
}
