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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.function.Function;

import javax.jcr.AccessDeniedException;
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

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Base class for processor that copies the values of certain answers from forms to the root of the resource JSON for
 * easy access from, for ex., the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/Copy<XXX>Answers/[questionnaire/subject type name]/} as properties with keys as names and a
 * references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by default.
 *
 * @version $Id$
 */
public abstract class AbstractAnswerCopyProcessor implements ResourceJsonProcessor
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnswerCopyProcessor.class);

    private static final String CONFIGURATION_PATH = "/apps/cards/config/CopyAnswers/";

    @Reference
    protected QuestionnaireUtils questionnaireUtils;

    @Reference
    protected FormUtils formUtils;

    @Reference
    protected SubjectUtils subjectUtils;

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
                final Node answer = getNodeAnswer(node, question);
                if (answer != null && answer.hasProperty("value")) {
                    final Object value = this.formUtils.getValue(answer);
                    if (value instanceof Long) {
                        json.add(key, (Long) value);
                    } else if (value instanceof Double) {
                        json.add(key, (Double) value);
                    } else if (value instanceof Calendar) {
                        json.add(key, DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.ofInstant(
                            ((Calendar) value).toInstant(), ((Calendar) value).getTimeZone().toZoneId())));
                    } else if (value != null) {
                        json.add(key, String.valueOf(value));
                    }
                }
            } catch (final RepositoryException e) {
                // Should not happen
                LOGGER.warn("Failed to access answer {}: {}", key, e.getMessage(), e);
            }
        }
    }

    private Node getNodeAnswer(final Node subject, final Node question)
    {
        final Node targetForm = findForm(subject, question);
        return this.formUtils.getAnswer(targetForm, question);
    }

    protected abstract Node findForm(Node source, Node question);

    protected abstract String getConfigurationPath(Resource resource) throws RepositoryException;
}
