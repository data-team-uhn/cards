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
package io.uhndata.cards.forms.internal.serialize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Simplify Form serialization by including only the name of the subject, questionnaire, and each section and question.
 * It should be used together with {@code deep}, {@code -dereference} and {@code -identify}. The name of this processor
 * is {@code bare}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class BareFormProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BareFormProcessor.class);

    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(HashMap::new);

    private ThreadLocal<Map<String, String>> questionNames = ThreadLocal.withInitial(HashMap::new);

    @Override
    public String getName()
    {
        return "bare";
    }

    @Override
    public String getDescription()
    {
        return "Only runs on `Forms`.\n"
            + "Simplify form serialization by only including the subject name, questionnaire and each section "
            + "and question. Designed for use with `deep`, `-dereference` and `-identify`.";
    }

    @Override
    public int getPriority()
    {
        return 95;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        // This only simplifies forms
        return resource.isResourceType("cards/Form");
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        if (property == null) {
            return null;
        }
        try {
            JsonValue result = input;
            result = simplifyQuestionnaire(node, property, result);
            result = simplifySubject(node, property, result);
            result = simplifyRelatedSubjects(node, property, result);
            result = simplifySection(node, property, result);
            result = simplifyQuestion(node, property, result);
            result = removeStatusFlags(node, property, result);
            return result;
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        if (input == null) {
            return null;
        }
        try {
            // Do not recursively serialize questionnaire items
            if (node.isNodeType("cards:Questionnaire") || node.isNodeType("cards:Section")
                || node.isNodeType("cards:Question")) {
                return null;
            }
            if (child.isNodeType("cards:Answer") || child.isNodeType("cards:AnswerSection")) {
                this.childrenJsons.get().put(child.getIdentifier(), input.asJsonObject());
                return null;
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        addSectionsAndAnswers(node, json);
    }

    @Override
    public void end(Resource resource)
    {
        this.childrenJsons.remove();
        this.questionNames.remove();
    }

    private void addSectionsAndAnswers(final Node node, final JsonObjectBuilder json)
    {
        try {
            // Repeatable sections may have different nodes scattered among the children.
            // The first time we encounter a section, we create a JsonArrayBuilder for it, and add it in the output.
            // Further instances of that section will be added to this JsonArrayBuilder.
            final Map<String, JsonArrayBuilder> sectionArrays = new HashMap<>();
            // The output is a JSON object keyed by human-readable label, but two different sections (or a section
            // and a question) can share a label; track the keys already used so that a clash disambiguates the
            // later entry instead of silently overwriting the earlier one. CARDS-2798.
            final Set<String> usedKeys = new HashSet<>();
            // A recurrent section keeps the same output key across all of its instances, resolved the first time.
            final Map<String, String> sectionKeys = new HashMap<>();
            final NodeIterator children = node.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final String childId = child.getIdentifier();
                if (child.isNodeType("cards:AnswerSection") && this.childrenJsons.get().containsKey(childId)) {
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    final String childLabel = childJson.getString("section");
                    final JsonObject filteredChildJson = Json.createObjectBuilder(childJson).remove("section").build();
                    Node section = child.getProperty("section").getNode();
                    if (section.hasProperty("recurrent") && section.getProperty("recurrent").getBoolean()) {
                        final String sectionId = section.getIdentifier();
                        final String key =
                            sectionKeys.computeIfAbsent(sectionId, id -> uniqueKey(childLabel, usedKeys));
                        final JsonArrayBuilder array =
                            sectionArrays.computeIfAbsent(sectionId, id -> Json.createArrayBuilder());
                        array.add(filteredChildJson);
                        // json.add will build the array and store the result, so further changes to it will not be
                        // reflected. We need to re-add it every time we modify the array.
                        json.add(key, array);
                    } else {
                        json.add(uniqueKey(childLabel, usedKeys), filteredChildJson);
                    }
                    this.childrenJsons.get().remove(childId);
                } else if (child.isNodeType("cards:Answer") && this.childrenJsons.get().containsKey(childId)) {
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    final String childLabel = this.questionNames.get().get(childId);
                    json.add(uniqueKey(childLabel, usedKeys), childJson);
                    this.childrenJsons.get().remove(childId);
                    this.questionNames.get().remove(childId);
                }
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

    /**
     * Compute a key for a serialized child that doesn't clash with a sibling already stored under the same label.
     * Since the bare serialization is a JSON object, two children sharing a label would otherwise overwrite each
     * other; when the label is already taken, a numeric suffix is appended so that both entries are kept.
     *
     * @param label the desired, human-readable key
     * @param usedKeys the keys already used in the current object; the returned key is added to it
     * @return {@code label} if it is still free, otherwise {@code label} followed by a free {@code  (n)} suffix
     */
    private static String uniqueKey(final String label, final Set<String> usedKeys)
    {
        String key = label;
        int suffix = 1;
        while (!usedKeys.add(key)) {
            key = label + " (" + (++suffix) + ")";
        }
        return key;
    }

    private JsonValue simplifyQuestionnaire(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the questionnaire reference with the title of the actual questionnaire
        if (node.isNodeType("cards:Form") && "questionnaire".equals(property.getName())) {
            return Json.createValue(property.getNode().getProperty("title").getString());
        }
        return input;
    }

    private JsonValue simplifySubject(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the subject reference with the label of the actual subject (and its parents)
        if (node.isNodeType("cards:Form") && "subject".equals(property.getName())) {
            final Node subject = property.getNode();
            return processSubject(subject);
        }
        return input;
    }

    private JsonValue simplifyRelatedSubjects(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the relatedSubjects reference with the array of label of the actual related subjects
        if (node.isNodeType("cards:Form") && "relatedSubjects".equals(property.getName())) {
            final JsonArrayBuilder builder = Json.createArrayBuilder();
            Value[] values = property.getValues();
            for (Value value : values) {
                try {
                    Node relatedSubject = node.getSession().getNodeByIdentifier(value.getString());
                    builder.add(processSubject(relatedSubject));
                } catch (Exception e) {
                    // The current user may not have access to all the referenced related nodes
                    LOGGER.debug("Skipping inaccessible related subject: {}", e.getMessage());
                }
            }
            return builder.build();
        }
        return input;
    }

    private JsonValue processSubject(final Node subject) throws RepositoryException
    {
        if (subject.hasProperty("fullIdentifier")) {
            return Json.createValue(subject.getProperty("fullIdentifier").getString());
        } else if (subject.hasProperty("identifier")) {
            return Json.createValue(subject.getProperty("identifier").getString());
        } else {
            return Json.createValue(subject.getName());
        }
    }

    private JsonValue simplifySection(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the section reference with the label of the actual section, if present, or the section node name
        // otherwise
        if (node.isNodeType("cards:AnswerSection") && "section".equals(property.getName())) {
            final Node section = property.getNode();
            if (section.hasProperty("label")) {
                return Json.createValue(section.getProperty("label").getString());
            }
            return Json.createValue(section.getName());
        }
        return input;
    }

    private JsonValue simplifyQuestion(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the question reference with the label of the actual question
        if (node.isNodeType("cards:Answer") && "question".equals(property.getName())) {
            Node question = property.getNode();
            this.questionNames.get().put(node.getIdentifier(), question.getName());
            return Json.createValue(question.getProperty("text").getString());
        }
        return input;
    }

    private JsonValue removeStatusFlags(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Status flags are not needed
        if ("statusFlags".equals(property.getName())) {
            return null;
        }
        return input;
    }
}
