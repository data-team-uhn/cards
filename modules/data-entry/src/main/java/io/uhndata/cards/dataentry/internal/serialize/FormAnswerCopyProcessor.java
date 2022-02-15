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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A processor that copies the values of certain answers to the root of the JSON for easy access from, for example, the
 * dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopyFormAnswers/[questionnaire name]/} as properties with the desired prop name as the key,
 * and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by
 * default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FormAnswerCopyProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormAnswerCopyProcessor.class);

    private static final String CONFIGURATION_NODE = "/apps/cards/config/CopyFormAnswers";

    private final ThreadLocal<Node> answersToCopy = ThreadLocal.withInitial(() -> null);

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private SubjectUtils subjectUtils;

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
                    this.answersToCopy.set(configuration.adaptTo(Node.class));
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

    private Node getAnswer(final Node form, final Node question)
    {
        final Node targetForm = findForm(form, question);
        return this.formUtils.getAnswer(targetForm, question);
    }

    private Node findForm(final Node sourceForm, final Node question)
    {
        Node targetQuestionnaire = this.questionnaireUtils.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return null;
        }
        try {
            if (targetQuestionnaire.isSame(this.formUtils.getQuestionnaire(sourceForm))) {
                return sourceForm;
            }
            // If the questionnaire answered by the current form is not the target one,
            // look for a related form answering that questionnaire belonging to the form's related subjects.
            Node nextSubject = this.formUtils.getSubject(sourceForm);
            while (true) {
                // We stop when we've reached the end of the subjects hierarchy
                if (!this.subjectUtils.isSubject(nextSubject)) {
                    return null;
                }
                // Look for a form answering the right questionnaire
                final PropertyIterator otherForms = nextSubject.getReferences(FormUtils.SUBJECT_PROPERTY);
                while (otherForms.hasNext()) {
                    final Node otherForm = otherForms.nextProperty().getParent();
                    if (targetQuestionnaire.isSame(this.formUtils.getQuestionnaire(otherForm))) {
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
}
