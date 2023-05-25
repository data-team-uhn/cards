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

import java.util.function.Function;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Base class for processor that copies the values of certain answers from forms to the root of the resource JSON for
 * easy access from, for example, the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopyAnswers/[resource type]/[questionnaire|subject type name]/} as properties with keys as
 * names and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled
 * by default.
 *
 * @version $Id$
 */
public abstract class AbstractAnswerCopyProcessor implements ResourceJsonProcessor
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnswerCopyProcessor.class);

    private static final String CONFIGURATION_PATH = "/apps/cards/config/CopyAnswers/";

    @Reference
    protected FormUtils formUtils;

    private final ThreadLocal<Node> answersToCopy = ThreadLocal.withInitial(() -> null);

    private final ThreadLocal<String> rootResourcePath = ThreadLocal.withInitial(() -> null);

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
    public void start(final Resource resource)
    {
        try {
            final String path = CONFIGURATION_PATH.concat(getConfigurationPath(resource));
            final Resource configuration = resource.getResourceResolver().getResource(path);
            if (configuration != null) {
                this.answersToCopy.set(configuration.adaptTo(Node.class));
                this.rootResourcePath.set(resource.getPath());
            }
        } catch (final Exception e) {
            LOGGER.warn("Cannot access configuration for AnswerCopyProcessor: {}", e.getMessage(), e);
        }
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.getPath().equals(this.rootResourcePath.get())) {
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
        this.rootResourcePath.remove();
    }

    private void copyAnswers(final Node node, final JsonObjectBuilder json) throws RepositoryException
    {
        final Node toCopy = this.answersToCopy.get();
        final PropertyIterator properties = toCopy.getProperties();
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            if (property.getType() != PropertyType.REFERENCE) {
                continue;
            }
            final String key = property.getName();
            try {
                final Node question = property.getNode();
                final Node answer = getAnswer(node, question);
                if (answer != null && answer.hasProperty("value")) {
                    final Property value = answer.getProperty("value");
                    if (value != null) {
                        json.add(key, this.formUtils.serializeProperty(value));
                    }
                }
            } catch (final ItemNotFoundException e) {
                // This is expected when we don't allow access to certain questions
            } catch (final RepositoryException e) {
                // Should not happen
                LOGGER.warn("Failed to access answer {}: {}", key, e.getMessage(), e);
            }
        }
    }

    /**
     * Find the first answer related to the target {@code source}, answering the target {@code question}.
     *
     * @param source the resource being serialized
     * @param question a question whose answer needs to be copied
     * @return a relevant answer node or {@code null}
     */
    protected abstract Node getAnswer(Node source, Node question);

    /**
     * Compute the relative path to the copy configuration that applies to the resource being serialized, excluding the
     * base {@link #CONFIGURATION_PATH configuration path}.
     *
     * @param resource the resource being serialized
     * @return a path relative to {@link #CONFIGURATION_PATH} where the configuration for the resource should be stored,
     *         may be {@code null} if the resource is not supported
     */
    protected abstract String getConfigurationPath(Resource resource);
}
