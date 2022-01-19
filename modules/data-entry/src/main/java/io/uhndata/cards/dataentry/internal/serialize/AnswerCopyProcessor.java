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
package io.uhndata.cards.dataentry.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A processor that copies the values of certain answers to the root of the JSON for easy access from, for example, the
 * dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopyAnswers/[questionnaire name]/} as properties with the desired property name as the key,
 * and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by
 * default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class AnswerCopyProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerCopyProcessor.class);

    private static final String CONFIGURATION_NODE = "/apps/cards/config/CopyAnswers";

    private final ThreadLocal<ValueMap> answersToCopy = ThreadLocal.withInitial(() -> ValueMap.EMPTY);

    @Reference
    private FormUtils formUtils;

    @Override
    public String getName()
    {
        return "answerCopy";
    }

    @Override
    public int getPriority()
    {
        return 95;
    }

    @Override
    public boolean isEnabledByDefault(final Resource resource)
    {
        return true;
    }

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on forms
        return resource.isResourceType("cards/Form");
    }

    @Override
    public void start(final Resource resource)
    {
        final Resource allConfigurations = resource.getResourceResolver().getResource(CONFIGURATION_NODE);
        if (allConfigurations != null) {
            try {
                final String questionnaireName =
                    resource.getValueMap().get(FormUtils.QUESTIONNAIRE_PROPERTY, Property.class).getNode().getName();
                final Resource configuration = allConfigurations.getChild(questionnaireName);
                if (configuration != null) {
                    this.answersToCopy.set(configuration.getValueMap());
                }
            } catch (final RepositoryException e) {
                LOGGER.warn("Cannot access configuration for AnswerCopyProcessor: {}", e.getMessage(), e);
            }
        }
        // TODO Auto-generated method stub
        ResourceJsonProcessor.super.start(resource);
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (this.answersToCopy.get() != null && node.isNodeType(FormUtils.FORM_NODETYPE)) {
                copyAnswers(node, json);
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
    }

    @Override
    public void end(final Resource resource)
    {
        this.answersToCopy.remove();
    }

    private void copyAnswers(final Node node, final JsonObjectBuilder json)
    {
        final ValueMap toCopy = this.answersToCopy.get();
        toCopy.keySet().forEach(key -> {
            try {
                final String question = toCopy.get(key, String.class);
                final Node answer = getAnswer(node, question);
                if (answer != null && answer.hasProperty("value")) {
                    final String value = answer.getProperty("value").getString();
                    if (value != null) {
                        json.add(key, value);
                    }
                }
            } catch (final RepositoryException e) {
                // Should not happen
                LOGGER.warn("Failed to access answer {}: {}", key, e.getMessage(), e);
            }
        });
    }

    private Node getAnswer(final Node form, final String question)
    {
        return findNode(form, "question", question);
    }

    private Node findNode(final Node parent, final String property, final String value)
    {
        try {
            if (parent.hasProperty(property)
                && StringUtils.equals(parent.getProperty(property).getValue().getString(), value)) {
                return parent;
            }
            final NodeIterator children = parent.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final Node result = findNode(child, property, value);
                if (result != null) {
                    return result;
                }
            }
        } catch (IllegalStateException | RepositoryException e) {
            // Not found or not accessible, just return null
        }
        return null;
    }
}
