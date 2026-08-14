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
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataRetriever;
import io.uhndata.cards.utils.DateUtils;

/**
 * Export all File Answers belonging to forms modified in the given time interval. The name of this data retriever is
 * {@code Modified file answers}. It is possible to restrict this to only forms for a specific questionnaire using
 * {@code questionnaire=/Questionnaires/ABC} {@link ExportConfigDefinition#retrieverParameters() retriever
 * configurations}, one for each targeted questionnaire. The produced {@link ResourceIdentifier}s are paths to the
 * attached file nodes.
 *
 * @version $Id$
 * @since 0.9.26
 */
@Component(immediate = true, service = DataRetriever.class)
public class ModifiedFileAnswersRetriever implements DataRetriever
{
    @Override
    public String getName()
    {
        return "Modified file answers";
    }

    @Override
    public List<ResourceIdentifier> getResourcesToExport(
        final ExportConfigDefinition config,
        final ZonedDateTime startDate, final ZonedDateTime endDate,
        final ResourceResolver resolver)
        throws RepositoryException
    {
        List<ResourceIdentifier> files = new LinkedList<>();
        // FIXME This doesn't take into account the questionnairesToBeExported setting
        String query = String.format(
            "SELECT file.* FROM [cards:FileAnswer] AS file INNER JOIN [cards:Form] AS form"
                + " ON file.form=form.[jcr:uuid]"
                + " WHERE form.[jcr:lastModified] >= '%s'"
                + (endDate != null ? " AND form.[jcr:lastModified] < '%s'" : "")
                // FIXME This is hardcoded for now, revisit once CARDS-2430 is done
                + " AND NOT form.[statusFlags] = 'INCOMPLETE'",
            DateUtils.toString(startDate), DateUtils.toString(endDate));

        final List<String> questionnaires = getNamedParameters(config.retrieverParameters(), "questionnaire");
        if (!questionnaires.isEmpty()) {
            query += questionnaires.stream()
                .map(s -> "form.questionnaire = '" + resolver.getResource(s).getValueMap().get("jcr:uuid") + "'")
                .collect(Collectors.joining(" OR ", " AND (", ")"));
        }
        query += " OPTION (index tag cards)";
        LoggerFactory.getLogger(ModifiedFileAnswersRetriever.class).error("Query: {}", query);

        NodeIterator results = resolver.adaptTo(Session.class).getWorkspace().getQueryManager()
            .createQuery(query, "JCR-SQL2").execute().getNodes();
        while (results.hasNext()) {
            Node file = results.nextNode();
            NodeIterator children = file.getNodes();
            while (children.hasNext()) {
                Node child = children.nextNode();
                if (child.isNodeType("nt:file")) {
                    String path = child.getPath();
                    files.add(new ResourceIdentifier(path, child.getName(), path));
                }
            }
        }
        LoggerFactory.getLogger(ModifiedFileAnswersRetriever.class).error("Found files: {}", files);
        return files;
    }
}
