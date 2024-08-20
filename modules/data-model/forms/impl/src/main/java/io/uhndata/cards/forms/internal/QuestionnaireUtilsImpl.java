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
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;

@Component
public final class QuestionnaireUtilsImpl extends AbstractNodeUtils implements QuestionnaireUtils
{
    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public Node getQuestionnaire(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrp));
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
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrp));
        return isSection(result) ? result : null;
    }

    @Override
    public boolean isSection(final Node node)
    {
        return isNodeType(node, SECTION_NODETYPE);
    }

    @Override
    public boolean isConditionalSection(final Node node)
    {
        if (isSection(node)) {
            try {
                final NodeIterator children = node.getNodes();
                while (children.hasNext()) {
                    final Node child = children.nextNode();
                    if (child.isNodeType("cards:Conditional") || child.isNodeType("cards:ConditionalGroup")) {
                        return true;
                    }
                }
            } catch (final RepositoryException e) {
                // Not expected
            }
        }
        return false;
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
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrp));
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

    @Override
    public Type<?> getAnswerType(final Node question)
    {
        Type<?> result = Type.STRING;
        try {
            final String dataTypeString = question.getProperty("dataType").getString();
            switch (dataTypeString) {
                case "long":
                    result = Type.LONG;
                    break;
                case "double":
                    result = Type.DOUBLE;
                    break;
                case "decimal":
                    result = Type.DECIMAL;
                    break;
                case "boolean":
                    // Long, not boolean
                    result = Type.LONG;
                    break;
                case "date":
                    result = (question.hasProperty("dateFormat") && "yyyy".equals(
                        question.getProperty("dateFormat").getString().toLowerCase()))
                            ? Type.LONG
                            : Type.DATE;
                    break;
                default:
                    result = Type.STRING;
            }
        } catch (RepositoryException e) {
            // Default to STRING
        }
        return result;
    }
}
