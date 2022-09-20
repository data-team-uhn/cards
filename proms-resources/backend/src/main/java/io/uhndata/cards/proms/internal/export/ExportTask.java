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

package io.uhndata.cards.proms.internal.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.CSVString;

public class ExportTask implements Runnable
{

    private static final long DAY_IN_MS = 1000 * 60 * 60 * 24;

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;
    private final int frequencyInDays;
    private final List<String> questionnairesToBeExported;
    private final String savePath;

    ExportTask(final ResourceResolverFactory resolverFactory, int frequencyInDays, String[] questionnairesToBeExported,
               String savePath)
    {
        this.resolverFactory = resolverFactory;
        this.frequencyInDays = frequencyInDays;
        this.questionnairesToBeExported = new ArrayList<>(Arrays.asList(questionnairesToBeExported));
        this.savePath = savePath;
    }

    @Override
    public void run()
    {
        Set<Resource> questionnaires = this.getQuestionnaires(generateQuestionnairesQuery());
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        final String modifiedAfterDate = simpleDateFormat.format(getLastExportDate());
        final String modifiedBeforeDate = simpleDateFormat.format(new Date());
        final File csvFile = new File(this.savePath + "/ExportedForms_" + modifiedAfterDate + ".csv");
        try (FileWriter writer = new FileWriter(csvFile)) {
            for (Resource questionnaire : questionnaires) {
                final String csvPath = String.format(
                        questionnaire.getPath() + ".data.dataFilter:modifiedAfter=%s.dataFilter:modifiedBefore=%s.csv",
                        modifiedAfterDate, modifiedBeforeDate);
                try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
                    final CSVString csv = resolver.resolve(csvPath).adaptTo(CSVString.class);
                    writer.write(csv.toString());
                } catch (LoginException | IOException e) {
                    LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String generateQuestionnairesQuery()
    {
        String query = null;
        if (this.questionnairesToBeExported != null && !this.questionnairesToBeExported.isEmpty()) {
            ListIterator<String> export = this.questionnairesToBeExported.listIterator();
            StringBuilder conditionQuery = new StringBuilder("SELECT * FROM [cards:Questionnaire] AS q "
                    + "WHERE q.[jcr:uuid] = '" + export.next() + "'");
            while (export.hasNext()) {
                conditionQuery.append(" or q.[jcr:uuid] = '").append(export.next()).append("'");
            }
            query = conditionQuery.toString();
        }
        return query;
    }

    private Set<Resource> getQuestionnaires(String query)
    {
        if (query != null) {
            try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
                Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
                Set<Resource> questionnaires = new HashSet<>();
                while (results.hasNext()) {
                    Resource obj = results.next();
                    questionnaires.add(obj);
                }
                return questionnaires;
            } catch (LoginException e) {
                LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
            }
        }
        return Collections.emptySet();
    }

    public Date getLastExportDate()
    {
        Calendar date = new GregorianCalendar();
        date.add(Calendar.DAY_OF_MONTH, (-(this.frequencyInDays + 1)));
        return date.getTime();
    }
}
