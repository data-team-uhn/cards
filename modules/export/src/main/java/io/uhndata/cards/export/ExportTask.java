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

package io.uhndata.cards.export;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.errortracking.ErrorLogger;
import io.uhndata.cards.export.spi.DataFormatter;
import io.uhndata.cards.export.spi.DataPipelineStep.ResourceIdentifier;
import io.uhndata.cards.export.spi.DataPipelineStep.ResourceRepresentation;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.export.spi.DataStore;
import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

public class ExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    private static final Pattern FORMATTED_NOW = Pattern.compile("\\{now\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_YESTERDAY = Pattern.compile("\\{yesterday\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_START = Pattern.compile("\\{start\\((.*?)\\)\\}");

    private static final Pattern FORMATTED_END = Pattern.compile("\\{end\\((.*?)\\)\\}");

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final ExportConfigDefinition config;

    private final DataRetriever retriever;

    private final DataFormatter formatter;

    private final DataStore store;

    private final String exportRunMode;

    private final LocalDate exportLowerBound;

    private final LocalDate exportUpperBound;

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final ExportConfigDefinition config, final DataPipeline pipeline, final String exportRunMode)
    {
        this(resolverFactory, rrp, config, pipeline, exportRunMode, null, null);
    }

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final ExportConfigDefinition config, final DataPipeline pipeline, final String exportRunMode,
        final LocalDate exportLowerBound, final LocalDate exportUpperBound)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.config = config;
        this.retriever = pipeline.getRetriever();
        this.formatter = pipeline.getFormatter();
        this.store = pipeline.getStore();
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = exportLowerBound;
        this.exportUpperBound = exportUpperBound;
    }

    @Override
    public void run()
    {
        try {
            if ("scheduled".equals(this.exportRunMode)) {
                doPeriodicExport();
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
        LOGGER.info("Executing Manual data Export {}", this.config.name());
        doExport(lower != null ? lower.atStartOfDay(ZoneId.systemDefault()) : null,
            upper != null ? upper.atStartOfDay(ZoneId.systemDefault()) : null);
    }

    public void doPeriodicExport() throws LoginException
    {
        LOGGER.info("Executing Scheduled data Export {}", this.config.name());
        doExport(getPastDayStart(this.config.frequencyInDays()), getPastDayStart(0));
    }

    public void doDailyExport() throws LoginException
    {
        LOGGER.info("Executing Daily S3 Export {}", this.config.name());
        doExport(getPastDayStart(0), null);
    }

    private void doExport(final ZonedDateTime startDate, final ZonedDateTime endDate) throws LoginException
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            List<ResourceIdentifier> resourcesToExport =
                this.retriever.getResourcesToExport(this.config, startDate, endDate, resolver);

            for (ResourceIdentifier identifier : resourcesToExport) {
                ResourceRepresentation resourceContents =
                    this.formatter.format(identifier, startDate, endDate, this.config, resolver);
                if (resourceContents != null) {
                    String filename =
                        getTargetFileName(identifier, startDate, endDate);
                    this.output(resourceContents, filename);
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access data: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private ZonedDateTime getPastDayStart(int numberOfDaysAgo)
    {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(numberOfDaysAgo);
    }

    private String cleanString(String input)
    {
        return input.replaceAll("[^A-Za-z0-9_.-]", "");
    }

    private String getTargetFileName(final ResourceIdentifier identifier, final ZonedDateTime startDate,
        final ZonedDateTime endDate)
    {
        String result = this.config.fileNameFormat()
            .replace("{resourceIdentifier}", cleanString(identifier.getIdentifier()))
            .replace("{resourceName}",
                cleanString(StringUtils.substringAfterLast(identifier.getPath(), "/")))
            .replace("{resourcePath}",
                cleanString(identifier.getPath().replace('/', '_').substring(1)))
            .replace("{questionnaire}",
                cleanString(identifier.getPath().replace('/', '_').substring(1)))
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
        return m.replaceAll(match -> DateTimeFormatter.ofPattern(match.group(1)).format(endDate));
    }

    private void output(ResourceRepresentation input, String filename)
    {
        try {
            this.store.store(input.getRepresentation(), filename, input.getMimeType(), this.config);
            input.getDataContents().forEach(form -> {
                LOGGER.info("Exported {}", form);
                Metrics.increment(this.resolverFactory, "S3ExportedForms", 1);
            });
            LOGGER.info("Exported {} to {}", input.getIdentifier().getPath(), filename);
            Metrics.increment(this.resolverFactory, "S3ExportedSubjects", 1);
        } catch (Exception e) {
            LOGGER.error("Failed to export {}: {}", input.getIdentifier().getPath(), e.getMessage(), e);
        }
    }
}
