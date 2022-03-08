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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;

/**
 * Base class for processor that copies the values of certain answers from forms to the root of the resource JSON
 * for easy access from, for ex., the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/Copy<XXX>Answers/[questionnaire/subject type name]/} as properties with keys as names
 * and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by
 * default.
 *
 * @version $Id$
 */
public abstract class AbstractAnswerCopyProcessor
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnswerCopyProcessor.class);

    protected final ThreadLocal<Node> answersToCopy = ThreadLocal.withInitial(() -> null);

    protected void startProcessor(final Resource resource, String configurationPath)
    {
        final Resource allConfigurations = resource.getResourceResolver().getResource(configurationPath);
        if (allConfigurations != null) {
            try {
                final String resourceName = getResourceName(resource);
                if (resourceName != null) {
                    final Resource configuration = allConfigurations.getChild(resourceName);
                    if (configuration != null) {
                        this.answersToCopy.set(configuration.adaptTo(Node.class));
                    }
                }
            } catch (final RepositoryException e) {
                LOGGER.warn("Cannot access configuration for AnswerCopyProcessor: {}", e.getMessage(), e);
            }
        }
    }

    protected void copyAnswers(final Node node, final JsonObjectBuilder json) throws RepositoryException
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
                    final Object value = getValue(answer);
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
        return getAnswer(targetForm, question);
    }

    private Node findForm(final Node source, final Node question)
    {
        Node targetQuestionnaire = getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return null;
        }
        try {
            if (targetQuestionnaire.isSame(getQuestionnaire(source))) {
                return source;
            }
            // If the questionnaire answered by the current form is not the target one,
            // look for a related form answering that questionnaire belonging to the form's related subjects.
            Node nextSubject = getSubject(source);
            while (true) {
                // We stop when we've reached the end of the subjects hierarchy
                if (!isSubject(nextSubject)) {
                    return null;
                }
                // Look for a form answering the right questionnaire
                final PropertyIterator otherForms = nextSubject.getReferences(FormUtils.SUBJECT_PROPERTY);
                while (otherForms.hasNext()) {
                    final Node otherForm = otherForms.nextProperty().getParent();
                    if (targetQuestionnaire.isSame(getQuestionnaire(otherForm))) {
                        return otherForm;
                    }
                }
                // Not found among the subject's forms, next look in the parent subject's forms
                nextSubject = nextSubject.getParent();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to look for the right answer to copy: {}", e.getMessage(), e);
        }
        return null;
    }

    abstract String getResourceName(Resource resource) throws RepositoryException;

    abstract Node getAnswer(Node form, Node question);

    abstract Node getSubject(Node source);

    abstract Node getQuestionnaire(Node form);

    abstract boolean isSubject(Node source);

    abstract Node getOwnerQuestionnaire(Node question);

    abstract Object getValue(Node answer);
}
