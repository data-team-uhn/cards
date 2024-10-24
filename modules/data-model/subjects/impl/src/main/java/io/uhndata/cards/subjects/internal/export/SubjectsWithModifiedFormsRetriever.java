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

package io.uhndata.cards.subjects.internal.export;

import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.utils.DateUtils;

@Component(immediate = true, service = DataRetriever.class)
public class SubjectsWithModifiedFormsRetriever implements DataRetriever
{
    @Override
    public String getName()
    {
        return "Subjects with modified forms";
    }

    @Override
    public List<ResourceIdentifier> getResourcesToExport(
        final ExportConfigDefinition config,
        final ZonedDateTime startDate, final ZonedDateTime endDate,
        final ResourceResolver resolver)
        throws RepositoryException
    {
        List<ResourceIdentifier> subjects = new LinkedList<>();
        // FIXME This doesn't take into account the questionnairesToBeExported setting
        String query = String.format(
            "SELECT subject.* FROM [cards:Form] AS form INNER JOIN [cards:Subject] AS subject"
                + " ON form.'subject'=subject.[jcr:uuid]"
                + " WHERE form.[jcr:lastModified] >= '%s'"
                + (endDate != null ? " AND form.[jcr:lastModified] < '%s'" : "")
                // FIXME This is hardcoded for now, revisit once CARDS-2430 is done
                + " AND NOT form.[statusFlags] = 'INCOMPLETE'"
                + " OPTION (INDEX TAG cards)",
            DateUtils.toString(startDate), DateUtils.toString(endDate));

        Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
        while (results.hasNext()) {
            Resource subject = results.next();
            String exportPath = String.format("%s%s.data.deep"
                + ".dataFilter:modifiedAfter=%s"
                + (endDate != null ? ".dataFilter:modifiedBefore=%s" : ""),
                subject.getPath(), getNamedParameter(config.retrieverParameters(), "selectors"),
                escapeForDataUrl(DateUtils.toString(startDate)), escapeForDataUrl(DateUtils.toString(endDate)));
            String participantId = subject.getValueMap().get("identifier", String.class);
            subjects.add(new ResourceIdentifier(subject.getPath(), participantId, exportPath));
        }
        return subjects;
    }
}
