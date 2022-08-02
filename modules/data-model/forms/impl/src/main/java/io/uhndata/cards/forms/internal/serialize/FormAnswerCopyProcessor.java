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

import java.util.Collection;
import java.util.EnumSet;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * A processor that copies the values of certain answers to the root of the form JSON for easy access from, for example,
 * the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopyAnswers/Questionnaires/[questionnaire name]/} as properties with the desired prop name
 * as the key, and a references to a question as the value. Questions can be copied either from the current form itself,
 * or from another form belonging to the related subjects. The name of this processor is {@code answerCopy} and it is
 * enabled by default.
 *
 * @version $Id$
 */
@Component(immediate = true, service = ResourceJsonProcessor.class,
    reference = {
        @Reference(name = "formUtils", service = FormUtils.class, field = "formUtils")
    })
public class FormAnswerCopyProcessor extends AbstractAnswerCopyProcessor
{
    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on forms
        return resource.isResourceType("cards/Form");
    }

    @Override
    protected String getConfigurationPath(final Resource resource)
    {
        try {
            return "Questionnaires/"
                + resource.getValueMap().get(FormUtils.QUESTIONNAIRE_PROPERTY, Property.class).getNode().getName();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to compute configuration path for form {}: {}", resource.getPath(), e.getMessage());
        }
        return null;
    }

    @Override
    protected Node getAnswer(Node source, Node question)
    {
        Collection<Node> answers =
            this.formUtils.findAllFormRelatedAnswers(source, question, EnumSet.allOf(FormUtils.SearchType.class));
        if (!answers.isEmpty()) {
            return answers.iterator().next();
        }
        return null;
    }
}
