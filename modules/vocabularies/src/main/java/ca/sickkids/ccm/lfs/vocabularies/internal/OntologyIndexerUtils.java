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

package ca.sickkids.ccm.lfs.vocabularies.internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyDescription;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyTermSource;

public final class OntologyIndexerUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OntologyIndexerUtils.class);

    //Hide the utility class constructor
    private OntologyIndexerUtils()
    {
    }

    /**
     * Creates a <code>VocabularyTerm</code> node representing an individual term of the vocabulary.
     *
     * @param term the term data
     * @param vocabularyNode must be passed from the calling class
     * @throws VocabularyIndexException when a node cannot be created
     */
    public static void createVocabularyTermNode(VocabularyTermSource term, InheritableThreadLocal<Node> vocabularyNode)
    {
        try {
            Node vocabularyTermNode;
            try {
                vocabularyTermNode = vocabularyNode.get()
                                         .addNode("./" + term.getId()
                                         .replaceAll("[^A-Za-z0-9_\\.]", ""), "lfs:VocabularyTerm");
            } catch (ItemExistsException e) {
                // Sometimes terms appear twice; we'll just update the existing node
                vocabularyTermNode = vocabularyNode.get().getNode(term.getId());
            }
            vocabularyTermNode.setProperty("identifier", term.getId());

            vocabularyTermNode.setProperty("label", term.getLabel());
            vocabularyTermNode.setProperty("parents", term.getParents());
            vocabularyTermNode.setProperty("ancestors", term.getAncestors());

            Iterator<Map.Entry<String, Collection<String>>> it = term.getAllProperties().asMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Collection<String>> entry = it.next();
                String[] valuesArray = entry.getValue().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
                // Sometimes the source may contain more than one label or description, but we can't allow that.
                // Always use one value for these special fields.
                if (("label".equals(entry.getKey()) || "description".equals(entry.getKey()))
                        && valuesArray.length == 1) {
                    vocabularyTermNode.setProperty(entry.getKey(), valuesArray[0]);
                } else {
                    vocabularyTermNode.setProperty(entry.getKey(), valuesArray);
                }
            }
        } catch (RepositoryException e) {
            // If the identifier exists, print the identifier in the error message to identify node
            LOGGER.warn("Failed to create VocabularyTerm node {}: {}", StringUtils.defaultString(term.getId()),
                e.getMessage());

        }
    }

    /**
     * Creates a <code>Vocabulary</code> node that represents the current vocabulary instance with the identifier as the
     * name of the node.
     *
     * @param homepage <code>VocabulariesHomepage</code> node instance that will be parent of the new vocabulary node
     * @param description the vocabulary description, holding all the relevant information about the vocabulary
     * @return the <code>Vocabulary</code> node that was created
     * @throws VocabularyIndexException when node cannot be created
     */
    public static Node createVocabularyNode(final Node homepage, final VocabularyDescription description)
        throws VocabularyIndexException
    {
        try {
            Node result = homepage.addNode("./" + description.getIdentifier(), "lfs:Vocabulary");
            result.setProperty("identifier", description.getIdentifier());
            result.setProperty("name", description.getName());
            result.setProperty("description", description.getDescription());
            result.setProperty("source", description.getSource());
            result.setProperty("version", description.getVersion());
            result.setProperty("website", description.getWebsite());
            result.setProperty("citation", description.getCitation());
            return result;
        } catch (RepositoryException e) {
            String message = "Failed to create Vocabulary node: " + e.getMessage();
            LOGGER.error(message, e);
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Saves the JCR session of the homepage node that was obtained from the resource of the request. If this is
     * successful, then the changes made already will be applied to the JCR repository. If not, then all of the changes
     * will be discarded, reverting to the original state.
     *
     * @param vocabulariesHomepage the <code>VocabulariesHomepage</code> node obtained from the request
     * @throws VocabularyIndexException if session is not successfully saved
     */
    public static void saveSession(Node vocabulariesHomepage)
        throws VocabularyIndexException
    {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            String message = "Failed to save session: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }
}