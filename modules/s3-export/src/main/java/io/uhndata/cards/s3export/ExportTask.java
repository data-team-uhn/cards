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

package io.uhndata.cards.s3export;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.StringUtils;
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
import io.uhndata.cards.serialize.CSVString;

public class ExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    private static final String DOT = "\\.";

    private static final DateTimeFormatter JCR_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private static final Pattern FORMATTED_NOW = Pattern.compile("\\{now\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_YESTERDAY = Pattern.compile("\\{yesterday\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_START = Pattern.compile("\\{start\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_END = Pattern.compile("\\{end\\((.*?)\\)\\}");

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final ExportConfigDefinition config;

    private final String exportRunMode;

    private final LocalDate exportLowerBound;

    private final LocalDate exportUpperBound;

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final ExportConfigDefinition config, final String exportRunMode)
    {
        this(resolverFactory, rrp, config, exportRunMode, null, null);
    }

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final ExportConfigDefinition config, final String exportRunMode,
        final LocalDate exportLowerBound, final LocalDate exportUpperBound)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.config = config;
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = exportLowerBound;
        this.exportUpperBound = exportUpperBound;
    }

    @Override
    public void run()
    {
        try {
            if ("scheduled".equals(this.exportRunMode)) {
                doScheduledExport();
            } else if ("today".equals(this.exportRunMode)) {
                doDailyExport();
            } else if ("manual".equals(this.exportRunMode)) {
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
        LOGGER.info("Executing Manual S3 Export {}", this.config.name());
        doExport(lower != null ? lower.atStartOfDay(ZoneId.systemDefault()) : null,
            upper != null ? upper.atStartOfDay(ZoneId.systemDefault()) : null);
    }

    public void doScheduledExport() throws LoginException
    {
        LOGGER.info("Executing Scheduled S3 Export {}", this.config.name());
        doExport(getPastDayStartString(this.config.frequencyInDays()), getPastDayStartString(0));
    }

    public void doDailyExport() throws LoginException
    {
        LOGGER.info("Executing Daily S3 Export {}", this.config.name());
        doExport(getPastDayStartString(0), null);
    }

    private void doExport(final ZonedDateTime startDate, final ZonedDateTime endDate) throws LoginException
    {
        final String startDateString = startDate == null ? null : startDate.format(JCR_DATE_FORMAT);
        final String endDateString = endDate == null ? null : endDate.format(JCR_DATE_FORMAT);
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
                    String filename =
                        getTargetFileName(cleanString(identifier.getSubjectId()), startDate, endDate);
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

    private ZonedDateTime getPastDayStartString(int numberOfDaysAgo)
    {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(numberOfDaysAgo);
    }

    private String cleanString(String input)
    {
        return input.replaceAll("[^A-Za-z0-9]", "");
    }

    private String escapeForDataUrl(String input)
    {
        return input.replaceAll(DOT, Matcher.quoteReplacement(DOT));
    }

    private String getTargetFileName(final String identifier, final ZonedDateTime startDate,
        final ZonedDateTime endDate)
    {

        String result = this.config.fileNameFormat()
            .replace("{subject}", cleanString(identifier))
            .replace("{questionnaire}", identifier.replace('/', '_'))
            .replace("{today}", DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now()))
            .replace("{yesterday}", DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now().minusDays(1)))
            .replace("{now}", DateTimeFormatter.ofPattern("HH-mm-ss").format(LocalDateTime.now()))
            .replace("{period}", DateTimeFormatter.ISO_LOCAL_DATE.format(startDate) + "_"
                + DateTimeFormatter.ISO_LOCAL_DATE.format(endDate == null ? LocalDateTime.now() : endDate));
        Matcher m = FORMATTED_NOW.matcher(result);
        result = m.replaceAll(match -> DateTimeFormatter.ofPattern(match.group(1)).format(ZonedDateTime.now()));
        m = FORMATTED_YESTERDAY.matcher(result);
        result = m
            .replaceAll(match -> DateTimeFormatter.ofPattern(match.group(1)).format(ZonedDateTime.now().minusDays(1)));
        m = FORMATTED_START.matcher(result);
        result = m.replaceAll(match -> DateTimeFormatter.ofPattern(match.group(1)).format(startDate));
        m = FORMATTED_END.matcher(result);
        result = m.replaceAll(match -> DateTimeFormatter.ofPattern(match.group(1)).format(endDate));
        return result + "." + this.config.exportFormat();
    }

    private static final class SubjectIdentifier
    {
        private String path;

        private String id;

        SubjectIdentifier(String path, String subjectId)
        {
            this.path = path;
            this.id = subjectId;
        }

        public String getPath()
        {
            return this.path;
        }

        public String getSubjectId()
        {
            return this.id;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.path.hashCode()) + Objects.hashCode(this.id.hashCode());
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
                && Objects.equals(this.id, other.getSubjectId());
        }

        @Override
        public String toString()
        {
            return String.format("{path:\"%s\",subjectId:\"%s\"}", this.path, this.id);
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
        // FIXME This doesn't take into account the questionnairesToBeExported setting
        String query = String.format(
            "SELECT subject.* FROM [cards:Form] AS form INNER JOIN [cards:Subject] AS subject"
                + " ON form.'subject'=subject.[jcr:uuid]"
                + " WHERE form.[jcr:lastModified] >= '%s'"
                + (requestDateStringUpper != null ? " AND form.[jcr:lastModified] < '%s'" : "")
                // FIXME This is hardcoded for now, revisit once CARDS-2430 is done
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
        // FIXME This doesn't take into account the questionnairesToBeExported setting
        final String subjectDataUrl = String.format("%s%s.data.deep"
            + ".dataFilter:modifiedAfter=%s" + (requestDateStringUpper != null ? ".dataFilter:modifiedBefore=%s" : "")
            // FIXME This is hardcoded for now, revisit once CARDS-2430 is done
            + ".dataFilter:statusNot=INCOMPLETE",
            path, StringUtils.defaultString(this.config.selectors()), escapeForDataUrl(requestDateStringLower),
            requestDateStringUpper != null ? escapeForDataUrl(requestDateStringUpper) : "");

        final String identifiedSubjectDataUrl = String.format("%s%s.data.identify.-properties.-dereference"
            + ".dataFilter:modifiedAfter=%s" + (requestDateStringUpper != null ? ".dataFilter:modifiedBefore=%s" : "")
            // FIXME This is hardcoded for now, revisit once CARDS-2430 is done
            + ".dataFilter:statusNot=INCOMPLETE",
            path, StringUtils.defaultString(this.config.selectors()), escapeForDataUrl(requestDateStringLower),
            requestDateStringUpper != null ? escapeForDataUrl(requestDateStringUpper) : "");
        final Resource subjectData = resolver.resolve(subjectDataUrl);
        final Resource identifiedSubjectData = resolver.resolve(identifiedSubjectDataUrl);
        final Class<?> c = "json".equals(this.config.exportFormat()) ? JsonObject.class : CSVString.class;
        return new SubjectContents(subjectData.adaptTo(c).toString(),
            identifiedSubjectData.adaptTo(JsonObject.class), subjectDataUrl);
    }

    private void output(SubjectContents input, String filename)
    {
        final String s3EndpointUrl = env(this.config.endpoint());
        final String s3EndpointRegion = env(this.config.region());
        final String s3BucketName = env(this.config.bucket());
        final String awsKey = env(this.config.accessKey());
        final String awsSecret = env(this.config.secretKey());
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

    private String env(final String value)
    {
        if (value != null && value.startsWith("%ENV%")) {
            return System.getenv(value.substring("%ENV%".length()));
        }
        return value;
    }
}
