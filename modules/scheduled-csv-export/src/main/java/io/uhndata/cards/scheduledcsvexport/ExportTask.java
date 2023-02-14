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

package io.uhndata.cards.scheduledcsvexport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.serialize.CSVString;

public class ExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final int frequencyInDays;

    private final List<String> questionnairesToBeExported;

    private final String savePath;

    private final String customSelectors;

    private final String fileNameFormat;

    private final String exportFormat;

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final ExportConfigDefinition config)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.frequencyInDays = config.frequency_in_days();
        this.questionnairesToBeExported = new ArrayList<>(Arrays.asList(config.questionnaires_to_be_exported()));
        this.customSelectors = config.selectors();
        this.savePath = config.save_path();
        this.fileNameFormat = config.file_name_format();
        this.exportFormat = config.export_format();
    }

    @Override
    public void run()
    {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        final String modifiedAfterDate = simpleDateFormat.format(getPastDate(this.frequencyInDays));
        final String modifiedBeforeDate = simpleDateFormat.format(new Date());
        final String timePeriod;
        if (this.frequencyInDays == 1) {
            timePeriod = simpleDateFormat.format(getPastDate(1));
        } else {
            final String endModificationDate = simpleDateFormat.format(getPastDate(1));
            timePeriod = modifiedAfterDate + "_" + endModificationDate;
        }
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            for (String questionnaire : this.questionnairesToBeExported) {
                final File csvFile = new File(
                    this.savePath + File.separatorChar + getTargetFileName(questionnaire, timePeriod));
                try (FileWriter writer = new FileWriter(csvFile)) {
                    final String csvPath = String.format(
                        questionnaire + "%s.data.dataFilter:modifiedAfter=%s.dataFilter:modifiedBefore=%s.%s",
                        StringUtils.defaultString(this.customSelectors), modifiedAfterDate, modifiedBeforeDate,
                        this.exportFormat);
                    final CSVString csv = resolver.resolve(csvPath).adaptTo(CSVString.class);
                    writer.write(csv.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }

    }

    private Date getPastDate(int numberOfDaysAgo)
    {
        Calendar date = new GregorianCalendar();
        date.add(Calendar.DAY_OF_MONTH, -numberOfDaysAgo);
        return date.getTime();
    }

    private String getTargetFileName(final String questionnaire, final String timePeriod)
    {
        return this.fileNameFormat
            .replace("{questionnaire}", StringUtils.removeStart(questionnaire.replace('/', '_'), "_"))
            .replace("{date}", DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now()))
            .replace("{time}", DateTimeFormatter.ISO_LOCAL_TIME.format(LocalDateTime.now()))
            .replace("{period}", timePeriod);
    }
}
