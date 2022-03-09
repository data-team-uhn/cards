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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

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
@Component(immediate = true, service = ResourceJsonProcessor.class,
    reference = {
        @Reference(name = "formUtils", service = FormUtils.class, field = "formUtils"),
        @Reference(name = "questionnaireUtils", service = QuestionnaireUtils.class, field = "questionnaireUtils"),
        @Reference(name = "subjectUtils", service = SubjectUtils.class, field = "subjectUtils")
    })
public class FormAnswerCopyProcessor extends AbstractAnswerCopyProcessor
{
    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on forms
        return resource.isResourceType("cards/Form");
    }

    @Override
    protected String getConfigurationPath(final Resource resource) throws RepositoryException
    {
        return "Questionnaires/"
            + resource.getValueMap().get(FormUtils.QUESTIONNAIRE_PROPERTY, Property.class).getNode().getName();
    }

    @Override
    protected Node findForm(final Node source, final Node question)
    {
        Node targetQuestionnaire = this.questionnaireUtils.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return null;
        }
        try {
            // If the question belongs to the current form's questionnaire, then the source itself is the target form
            if (targetQuestionnaire.isSame(this.formUtils.getQuestionnaire(source))) {
                return source;
            }
            // If the questionnaire answered by the current form is not the target one,
            // look for a related form answering that questionnaire belonging to the form's related subjects.
            Node nextSubject = this.formUtils.getSubject(source);
            // We stop when we've reached the end of the subjects hierarchy
            while (this.subjectUtils.isSubject(nextSubject)) {
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
