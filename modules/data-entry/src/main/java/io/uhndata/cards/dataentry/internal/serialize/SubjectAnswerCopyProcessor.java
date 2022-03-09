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

import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectTypeUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A processor that copies the values of certain answers from forms to the root of the subject JSON for easy access
 * from, for ex., the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopySubjectAnswers/[subject type]/} as properties with the desired name as the key, and a
 * references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by default.
 *
 * @version $Id$
 */
@Component(immediate = true, service = ResourceJsonProcessor.class,
    reference = {
        @Reference(name = "formUtils", service = FormUtils.class, field = "formUtils"),
        @Reference(name = "questionnaireUtils", service = QuestionnaireUtils.class, field = "questionnaireUtils"),
        @Reference(name = "subjectUtils", service = SubjectUtils.class, field = "subjectUtils")
    })
public class SubjectAnswerCopyProcessor extends AbstractAnswerCopyProcessor
{
    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on subjects
        return resource.isResourceType("cards/Subject");
    }

    @Override
    public void start(final Resource resource)
    {
        startProcessor(resource, "SubjectTypes");
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (this.answersToCopy.get() != null && node.isNodeType(SubjectUtils.SUBJECT_NODETYPE)) {
                copyAnswers(node, json);
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
    }

    @Override
    protected String getResourceType(final Resource resource) throws RepositoryException
    {
        Deque<String> types = new LinkedList<>();
        Node currentSubjectType = this.subjectUtils.getType(resource.adaptTo(Node.class));
        while (this.subjectTypeUtils.isSubjectType(currentSubjectType)) {
            types.push(this.subjectTypeUtils.getLabel(currentSubjectType));
            currentSubjectType = currentSubjectType.getParent();
        }
        if (!types.isEmpty()) {
            return types.stream().reduce((result, child) -> result + "/" + child).get();
        }
        return null;
    }

    @Override
    protected Node findForm(final Node source, final Node question)
    {
        Node targetQuestionnaire = this.questionnaireUtils.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return null;
        }
        try {
            Node currentSource = source;
            while (this.subjectUtils.isSubject(currentSource)) {
                // Look for all forms answering the right questionnaire belonging to the subject
                final PropertyIterator forms = currentSource.getReferences(FormUtils.SUBJECT_PROPERTY);
                while (forms.hasNext()) {
                    final Node form = forms.nextProperty().getParent();
                    final Node formQuestionnaire = this.formUtils.getQuestionnaire(form);
                    if (targetQuestionnaire.isSame(formQuestionnaire)) {
                        return form;
                    }
                }
                currentSource = currentSource.getParent();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to look for the right answer to copy: {}", e.getMessage(), e);
        }
        return null;
    }

}
