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

package io.uhndata.cards.vocabularies.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyTermSource;

public final class OntologyIndexerUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OntologyIndexerUtils.class);

    /** The list which holds all JCR vocabulary nodes associated with a vocabulary to be checked-in. */
    private static final ThreadLocal<List<Node>> NODES_TO_CHECK_IN = ThreadLocal.withInitial(ArrayList::new);

    /** The list which holds all root terms. */
    private static final ThreadLocal<List<Node>> ROOT_NODES = ThreadLocal.withInitial(ArrayList::new);

    //Hide the utility class constructor
    private OntologyIndexerUtils()
    {
    }

    /**
     * Creates a <code>VocabularyTerm</code> node representing an individual term of the vocabulary.
     *
     * @param term the term data
     * @param vocabularyNode must be passed from the calling class
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    public static void createVocabularyTermNode(VocabularyTermSource term, InheritableThreadLocal<Node> vocabularyNode)
    {
        try {
            Node vocabularyTermNode;
            try {
                vocabularyTermNode = vocabularyNode.get()
                                         .addNode("./" + term.getId()
                                         .replaceAll("[^A-Za-z0-9_\\.]", ""), "cards:VocabularyTerm");
            } catch (ItemExistsException e) {
                // Sometimes terms appear twice; we'll just update the existing node
                vocabularyTermNode = vocabularyNode.get().getNode(term.getId());
            }

            NODES_TO_CHECK_IN.get().add(vocabularyTermNode);
            vocabularyTermNode.setProperty("identifier", term.getId());

            vocabularyTermNode.setProperty("label", term.getLabel());

            String[] parents = term.getParents();
            boolean isObsolete = term.getAllProperties().asMap().get("is_obsolete") != null
                              || term.getLabel().toLowerCase().startsWith("obsolete");
            if ((parents.length == 0 || parents.length == 1 && "Thing".equals(parents[0])) && !isObsolete) {
                vocabularyTermNode.setProperty("isRoot", true);
                ROOT_NODES.get().add(vocabularyTermNode);
            }
            vocabularyTermNode.setProperty("parents", parents);
            vocabularyTermNode.setProperty("ancestors", term.getAncestors());

            Iterator<Map.Entry<String, Collection<String>>> it = term.getAllProperties().asMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Collection<String>> entry = it.next();
                String[] valuesArray = entry.getValue().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
                // Sometimes the source may contain more than one label or description, but we can't allow that.
                // Always use one value for these special fields.
                if (("label".equals(entry.getKey()) || "description".equals(entry.getKey())
                    || "isRoot".equals(entry.getKey()))
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
            Node result = homepage.addNode("./" + description.getIdentifier(), "cards:Vocabulary");
            result.setProperty("identifier", description.getIdentifier());
            result.setProperty("name", description.getName());
            result.setProperty("description", description.getDescription());
            result.setProperty("source", description.getSource());
            result.setProperty("version", description.getVersion());
            result.setProperty("website", description.getWebsite());
            result.setProperty("citation", description.getCitation());
            NODES_TO_CHECK_IN.get().add(result);
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
    private static void saveSession(Node vocabulariesHomepage)
        throws VocabularyIndexException
    {
        try {
            vocabulariesHomepage.getSession().save();
        } catch (RepositoryException e) {
            String message = "Failed to save session: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Checks into JCR the list of JCR Nodes associated with the installation of a vocabulary.
     *
     * @param vocabulariesHomepage the <code>VocabulariesHomepage</code> node obtained from the request
     * @throws VocabularyIndexException if the checking-in of a Node fails
     */
    private static void checkInVocabulary(Node vocabulariesHomepage) throws VocabularyIndexException
    {
        try {
            final VersionManager vm = vocabulariesHomepage.getSession().getWorkspace().getVersionManager();
            for (int i = 0; i < NODES_TO_CHECK_IN.get().size(); i++) {
                vm.checkin(NODES_TO_CHECK_IN.get().get(i).getPath());
            }
        } catch (RepositoryException e) {
            String message = "Failed to check-in vocabulary: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } finally {
            //Cleanup
            NODES_TO_CHECK_IN.remove();
        }
    }

    /**
     * Finalizes the vocabulary install by saving the JCR session and checking in all the newly installed
     * Vocabulary nodes.
     *
     * @param vocabulariesHomepage the <code>VocabulariesHomepage</code> node obtained from the request
     * @param vocabularyNode The vocabulary node that holds indexed data
     * @throws VocabularyIndexException if the JCR session is not successfully saved or the checking-in of a Node fails
     */
    public static void finalizeInstall(Node vocabulariesHomepage, InheritableThreadLocal<Node> vocabularyNode)
        throws VocabularyIndexException
    {
        setRootNodes(vocabularyNode);
        saveSession(vocabulariesHomepage);
        checkInVocabulary(vocabulariesHomepage);
    }

    /**
     * Sets root Vocabulary nodes.
     * @param vocabularyNode The vocabulary node that holds indexed data
     */
    private static void setRootNodes(InheritableThreadLocal<Node> vocabularyNode)
    {
        Value[] roots = ROOT_NODES.get().stream()
                                        .map(item ->
                                        {
                                            try {
                                                return item.getSession().getValueFactory().createValue(item);
                                            } catch (Exception e) {
                                                // do nothing
                                            }
                                            return null;
                                        })
                                        .collect(Collectors.toList()).toArray(new Value[0]);
        //Cleanup
        ROOT_NODES.remove();
        try {
            vocabularyNode.get().setProperty("roots", roots);
        } catch (Exception e) {
            LOGGER.error("Failed to set vocabulary roots: {}", e.getMessage(), e);
        }
    }
}
