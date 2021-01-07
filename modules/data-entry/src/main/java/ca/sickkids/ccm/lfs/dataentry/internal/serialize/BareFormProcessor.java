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
package ca.sickkids.ccm.lfs.dataentry.internal.serialize;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import ca.sickkids.ccm.lfs.serialize.spi.ResourceJsonProcessor;

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
    private ThreadLocal<Map<String, JsonObject>> childrenJsons = ThreadLocal.withInitial(HashMap::new);

    private ThreadLocal<Map<String, String>> questionNames = ThreadLocal.withInitial(HashMap::new);

    @Override
    public String getName()
    {
        return "bare";
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
        return resource.isResourceType("lfs/Form");
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
            if (node.isNodeType("lfs:Questionnaire") || node.isNodeType("lfs:Section")
                || node.isNodeType("lfs:Question")) {
                return null;
            }
            if (child.isNodeType("lfs:Answer") || child.isNodeType("lfs:AnswerSection")) {
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

    private void addSectionsAndAnswers(Node node, JsonObjectBuilder json)
    {
        try {
            final NodeIterator children = node.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final String childId = child.getIdentifier();
                if (child.isNodeType("lfs:AnswerSection") && this.childrenJsons.get().containsKey(childId)) {
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    final String childLabel = childJson.getString("section");
                    json.add(childLabel, Json.createObjectBuilder(childJson).remove("section").build());
                    this.childrenJsons.get().remove(childId);
                } else if (child.isNodeType("lfs:Answer") && this.childrenJsons.get().containsKey(childId)) {
                    final JsonObject childJson = this.childrenJsons.get().get(childId);
                    final String childLabel = this.questionNames.get().get(childId);
                    json.add(childLabel, childJson);
                    this.childrenJsons.get().remove(childId);
                    this.questionNames.get().remove(childId);
                }
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

    private JsonValue simplifyQuestionnaire(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the questionnaire reference with the title of the actual questionnaire
        if (node.isNodeType("lfs:Form") && "questionnaire".equals(property.getName())) {
            return Json.createValue(property.getNode().getProperty("title").getString());
        }
        return input;
    }

    private JsonValue simplifySubject(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the subject reference with the label of the actual subject (and its parents)
        if (node.isNodeType("lfs:Form") && "subject".equals(property.getName())) {
            return Json.createValue(property.getNode().getProperty("fullIdentifier").getString());
        }
        return input;
    }

    private JsonValue simplifySection(final Node node, final Property property, final JsonValue input)
        throws RepositoryException
    {
        // Replace the section reference with the label of the actual section, if present, or the section node name
        // otherwise
        if (node.isNodeType("lfs:AnswerSection") && "section".equals(property.getName())) {
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
        if (node.isNodeType("lfs:Answer") && "question".equals(property.getName())) {
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
