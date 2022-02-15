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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
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
 * A processor that copies the values of certain answers from forms to the root of the subject JSON
 * for easy access from, for ex., the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopySubjectAnswers/[questionnaire name]/} as properties with the desired name as the key,
 * and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by
 * default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SubjectAnswerCopyProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectAnswerCopyProcessor.class);

    private static final String CONFIGURATION_NODE = "/apps/cards/config/CopySubjectAnswers";

    private final ThreadLocal<List<Node>> answersToCopy = ThreadLocal.withInitial(() -> null);

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

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
        // This only works on subjects
        return resource.isResourceType("cards/Subject");
    }

    @Override
    public void start(final Resource resource)
    {
        final Resource allConfigurations = resource.getResourceResolver().getResource(CONFIGURATION_NODE);
        if (allConfigurations != null) {
            final Iterator<Resource> configurations = allConfigurations.getChildren().iterator();
            final List<Node> result = new ArrayList<>();
            while (configurations.hasNext()) {
                result.add(configurations.next().adaptTo(Node.class));
            }
            this.answersToCopy.set(result);
        }
        // TODO Auto-generated method stub
        ResourceJsonProcessor.super.start(resource);
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (this.answersToCopy.get() != null && node.isNodeType(SubjectUtils.SUBJECT_NODETYPE)) {
                processCopyConfigurations(node, json);
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

    private void processCopyConfigurations(final Node node, final JsonObjectBuilder json) throws RepositoryException
    {
        final List<Node> configurations = this.answersToCopy.get();
        for (Node configNode : configurations) {
            copyAnswers(node, json, configNode);
        }
    }

    private void copyAnswers(final Node node, final JsonObjectBuilder json, final Node toCopy)
        throws RepositoryException
    {
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

    private Node getAnswer(final Node subject, final Node question)
    {
        final Node targetForm = findForm(subject, question);
        return this.formUtils.getAnswer(targetForm, question);
    }

    private Node findForm(final Node sourceSubject, final Node question)
    {
        Node targetQuestionnaire = this.questionnaireUtils.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return null;
        }
        try {
            Node nextSubject = sourceSubject;
            while (true) {
                // We stop when we've reached the end of the subjects hierarchy
                if (!this.subjectUtils.isSubject(nextSubject)) {
                    return null;
                }
                // Look for all forms answering the right questionnaire belonging to the subject
                final PropertyIterator forms = sourceSubject.getReferences(FormUtils.SUBJECT_PROPERTY);
                while (forms.hasNext()) {
                    final Node form = forms.nextProperty().getParent();
                    if (targetQuestionnaire.isSame(this.formUtils.getQuestionnaire(form))) {
                        return form;
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
