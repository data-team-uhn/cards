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
package io.uhndata.cards.vocabularies.spi;

import java.io.IOException;
import java.io.Writer;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.component.annotations.Component;

/**
 * Utility methods for vocabulary parsing.
 *
 * @version $Id$
 */
@Component(service = VocabularyParserUtils.class)
public class VocabularyParserUtils
{

    /**
     * Remove any previous instances of the vocabulary which is to be parsed and indexed in the JCR repository by
     * deleting the vocabulary node instance. This will also cause the node's children to be deleted. An exception is
     * thrown if the overwrite parameter is not enabled and a vocabulary of the given name already exists in the
     * repository.
     *
     * @param homepage an instance of the VocabulariesHomepage node serving as the root of Vocabulary nodes
     * @param name the identifier of the vocabulary which will become its node name
     * @param overwrite signals whether a pre-existing vocabulary is to be overwritten by one with the same name
     * @throws VocabularyIndexException thrown when node cannot be removed
     */
    public void clearVocabularyNode(final Node homepage, final String name, final String overwrite)
        throws VocabularyIndexException
    {
        try {
            // Only delete the node if it exists
            if (homepage.hasNode(name)) {
                if (overwrite != null && "true".equalsIgnoreCase(overwrite)) {
                    Node target = homepage.getNode(name);
                    target.remove();
                } else {
                    throw new VocabularyIndexException("The identifier you specified already exists in the"
                        + " repository and you did not ");
                }
            }
        } catch (RepositoryException e) {
            String message = "Error: Failed to delete existing Vocabulary node. " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Writes a json to the http response consisting of two entries.
     * <p>
     * <code>isSuccessful</code> - true if the parsing attempt was successful; false otherwise
     * </p>
     * <p>
     * <code>error</code> - error message of the exception causing the failure; null if there is no exception
     * </p>
     *
     * @param request http request from the VocabularyIndexerServlet
     * @param response http response from the VocabularyIndexerServlet
     * @param isSuccessful boolean variable which is true if parsing is successful and false otherwise
     * @param error the error message caught from the exception which is null if there is no error
     * @throws IOException thrown when json cannot be written
     */
    public void writeStatusJson(final SlingHttpServletRequest request, final SlingHttpServletResponse response,
        final boolean isSuccessful, final String error) throws IOException
    {
        response.setStatus(isSuccessful
            ? SlingHttpServletResponse.SC_OK : SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        Writer out = response.getWriter();
        JsonGenerator generator = Json.createGenerator(out);
        generator.writeStartObject();
        generator.write("isSuccessful", isSuccessful);
        generator.write("error", error);
        generator.writeEnd();
        generator.flush();
    }
}
