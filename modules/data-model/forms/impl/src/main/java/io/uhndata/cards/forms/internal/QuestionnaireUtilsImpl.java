/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.forms.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.spi.AbstractNodeUtils;

@Component
public final class QuestionnaireUtilsImpl extends AbstractNodeUtils implements QuestionnaireUtils
{
    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Override
    public Node getQuestionnaire(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrf));
        return isQuestionnaire(result) ? result : null;
    }

    @Override
    public boolean isQuestionnaire(final Node node)
    {
        return isNodeType(node, QUESTIONNAIRE_NODETYPE);
    }

    @Override
    public boolean belongs(final Node element, final Node questionnaire)
    {
        try {
            if (element != null && questionnaire != null && isQuestionnaire(questionnaire)) {
                return element.getPath().startsWith(questionnaire.getPath() + "/");
            }
        } catch (RepositoryException e) {
            // Not critical, just ignore it
        }
        return false;
    }

    @Override
    public Node getOwnerQuestionnaire(final Node element)
    {
        try {
            Node target = element;
            while (target != null && !isQuestionnaire(target)) {
                target = target.getParent();
            }
            return target;
        } catch (RepositoryException e) {
            // Not critical, just return null
        }
        return null;
    }

    @Override
    public Node getSection(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrf));
        return isSection(result) ? result : null;
    }

    @Override
    public boolean isSection(final Node node)
    {
        return isNodeType(node, SECTION_NODETYPE);
    }

    @Override
    public Node getQuestion(final Node questionnaire, final String relativePath)
    {
        try {
            if (isQuestionnaire(questionnaire)) {
                final Node question = questionnaire.getNode(relativePath);
                // Only return if this is indeed a question that is part of the questionnaire
                if (isQuestion(question) && question.getPath().startsWith(questionnaire.getPath() + "/")) {
                    return question;
                }
            }
        } catch (final RepositoryException e) {
            // Not found or not accessible, just return null
        }
        return null;
    }

    @Override
    public boolean isQuestion(final Node node)
    {
        return isNodeType(node, QUESTION_NODETYPE);
    }

    @Override
    public boolean isComputedQuestion(final Node node)
    {
        try {
            return isQuestion(node)
                && ("computed".equals(node.getProperty("dataType").getString())
                    || "computed".equals(node.getProperty("entryMode").getString()));
        } catch (final RepositoryException e) {
            return false;
        }
    }

    @Override
    public boolean isReferenceQuestion(final Node node)
    {
        try {
            return isQuestion(node)
                && ("reference".equals(node.getProperty("entryMode").getString()));
        } catch (final RepositoryException e) {
            return false;
        }
    }

    @Override
    public Node getQuestion(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrf));
        return isQuestion(result) ? result : null;
    }

    @Override
    public String getQuestionName(final Node question)
    {
        try {
            return isQuestion(question) ? question.getName() : null;
        } catch (final RepositoryException e) {
            return null;
        }
    }

    @Override
    public String getQuestionText(final Node question)
    {
        return isQuestion(question) ? StringUtils.defaultString(getStringProperty(question, "text")) : "";
    }

    @Override
    public String getQuestionDescription(final Node question)
    {
        return isQuestion(question) ? StringUtils.defaultString(getStringProperty(question, "description")) : "";
    }
}
