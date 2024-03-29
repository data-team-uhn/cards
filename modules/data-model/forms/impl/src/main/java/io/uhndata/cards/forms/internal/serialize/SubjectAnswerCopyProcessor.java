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
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedList;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * A processor that copies the values of certain answers from forms to the root of the subject JSON for easy access
 * from, for example, the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopyAnswers/SubjectTypes/[subject type path]/} as properties with the desired name as the
 * key, and a references to a question as the value. Answers can be copied from a form belonging either to this subject,
 * or one of its ancestors. The name of this processor is {@code answerCopy} and it is enabled by default.
 *
 * @version $Id$
 */
@Component(immediate = true, service = ResourceJsonProcessor.class,
    reference = {
        @Reference(name = "formUtils", service = FormUtils.class, field = "formUtils")
    })
public class SubjectAnswerCopyProcessor extends AbstractAnswerCopyProcessor
{
    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on subjects
        return resource.isResourceType("cards/Subject");
    }

    @Override
    protected String getConfigurationPath(final Resource resource)
    {
        try {
            final Deque<String> types = new LinkedList<>();
            Node currentSubjectType = this.subjectUtils.getType(resource.adaptTo(Node.class));
            while (this.subjectTypeUtils.isSubjectType(currentSubjectType)) {
                types.push(this.subjectTypeUtils.getLabel(currentSubjectType));
                currentSubjectType = currentSubjectType.getParent();
            }
            if (!types.isEmpty()) {
                types.push("SubjectTypes");
                return types.stream().reduce((result, child) -> result + "/" + child).get();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to compute configuration path for subject {}: {}", resource.getPath(), e.getMessage());
        }
        return null;
    }

    @Override
    protected Node getAnswer(Node source, Node question)
    {
        Collection<Node> answers =
            this.formUtils.findAllSubjectRelatedAnswers(source, question, EnumSet.allOf(FormUtils.SearchType.class));
        if (!answers.isEmpty()) {
            return answers.iterator().next();
        }
        return null;
    }
}
