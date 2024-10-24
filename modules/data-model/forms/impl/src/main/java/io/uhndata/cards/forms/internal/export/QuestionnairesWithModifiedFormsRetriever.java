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

package io.uhndata.cards.forms.internal.export;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.utils.DateUtils;

@Component(immediate = true, service = DataRetriever.class)
public class QuestionnairesWithModifiedFormsRetriever implements DataRetriever
{
    @Override
    public String getName()
    {
        return "Questionnaires with modified forms";
    }

    @Override
    public List<ResourceIdentifier> getResourcesToExport(
        final ExportConfigDefinition config,
        final ZonedDateTime startDate, final ZonedDateTime endDate,
        final ResourceResolver resolver)
        throws RepositoryException
    {
        List<ResourceIdentifier> questionnaires = new LinkedList<>();
        // FIXME This uses all configured questionnaires, even if they don't have any new data
        for (String questionnaire : getNamedParameters(config.retrieverParameters(), "questionnaire")) {
            final String csvPath = String.format(
                questionnaire + "%s.data.dataFilter:modifiedAfter=%s"
                    + (endDate != null ? ".dataFilter:modifiedBefore=%s" : ""),
                StringUtils.defaultString(getNamedParameter(config.retrieverParameters(), "selectors")),
                escapeForDataUrl(DateUtils.toString(startDate)),
                escapeForDataUrl(DateUtils.toString(endDate)));
            questionnaires
                .add(new ResourceIdentifier(questionnaire, resolver.getResource(questionnaire).getName(), csvPath));
        }

        return questionnaires;
    }
}
