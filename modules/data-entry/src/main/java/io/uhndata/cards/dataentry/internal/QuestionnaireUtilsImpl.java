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
package io.uhndata.cards.dataentry.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

@Component(service = QuestionnaireUtils.class)
public final class QuestionnaireUtilsImpl extends AbstractNodeUtils implements QuestionnaireUtils
{
    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Override
    public boolean isQuestionnaire(final Node node)
    {
        return isNodeType(node, QUESTIONNAIRE_NODETYPE);
    }

    @Override
    public Node getQuestionnaire(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier);
        return isQuestionnaire(result) ? result : null;
    }

    @Override
    public boolean isSection(Node node)
    {
        return isNodeType(node, SECTION_NODETYPE);
    }

    @Override
    public Node getSection(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier);
        return isSection(result) ? result : null;
    }

    @Override
    public boolean isQuestion(Node node)
    {
        return isNodeType(node, QUESTION_NODETYPE);
    }


    @Override
    public boolean isComputedQuestion(Node node)
    {
        try {
            return isQuestion(node)
                && ("computed".equals(node.getProperty("dataType").getString())
                || "computed".equals(node.getProperty("entryMode").getString()));
        } catch (RepositoryException e) {
            return false;
        }
    }

    @Override
    public Node getQuestion(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier);
        return isQuestion(result) ? result : null;
    }

    @Override
    public String getQuestionName(final Node question)
    {
        try {
            return isQuestion(question) ? question.getName() : null;
        } catch (RepositoryException e) {
            return null;
        }
    }

    @Override
    public String getQuestionText(Node question)
    {
        return isQuestion(question) ? StringUtils.defaultString(getStringProperty(question, "text")) : "";
    }

    @Override
    public String getQuestionDescription(Node question)
    {
        return isQuestion(question) ? StringUtils.defaultString(getStringProperty(question, "description")) : "";
    }

    private Node getNodeByIdentifier(final String identifier)
    {
        try {
            final Session session = getSession(this.rrf);
            if (session == null) {
                return null;
            }
            return session.getNodeByIdentifier(identifier);
        } catch (RepositoryException e) {
            return null;
        }
    }
}
