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
package io.uhndata.cards.vocabularies;

import java.io.IOException;
import java.util.List;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexer;
import io.uhndata.cards.vocabularies.spi.VocabularyParserUtils;

/**
 * Servlet which handles indexing vocabularies. It processes POST requests on the {@code /Vocabularies} homepage,
 * expecting at least a {@code source} request parameter for identifying the vocabulary to index. Additional parameters
 * may influence the indexing process or outcome, depending on the source, such as {@code version}, {@code citation},
 * and {@code website}. The actual indexing is handled by implementations of the {@link VocabularyIndexer} service
 * interface.
 *
 * @see VocabularyIndexer
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/VocabulariesHomepage" }, methods = { "POST" })
public class VocabularyIndexerServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -2156160697967947088L;

    private static final Logger LOGGER = LoggerFactory.getLogger(VocabularyIndexerServlet.class);

    /**
     * Automatically injected list of all available indexers. Each of these will be tried in order until one of them can
     * successfully index a vocabulary based on the parameters of the request. A {@code volatile} list dynamically
     * changes when implementations are added, removed, or replaced.
     */
    @Reference
    private volatile List<VocabularyIndexer> indexers;

    @Reference
    private VocabularyParserUtils utils;

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        boolean success = false;
        final String source = request.getParameter("source");
        for (VocabularyIndexer indexer : this.indexers) {
            if (indexer.canIndex(source)) {
                try {
                    indexer.index(source, request, response);
                    // No exception means that the indexer either successfully finished,
                    // or encountered an exception and already printed a failure message
                    success = true;
                    break;
                } catch (VocabularyIndexException e) {
                    LOGGER.warn("Failed to index vocabulary from [{}] using indexer [{}]: {}", source,
                        indexer.getClass().getCanonicalName(), e.getMessage());
                }
            }
        }
        if (!success) {
            // No indexer could process the request, output an error message
            this.utils.writeStatusJson(request, response, false, "No valid indexer for source " + source);
        }
    }
}
