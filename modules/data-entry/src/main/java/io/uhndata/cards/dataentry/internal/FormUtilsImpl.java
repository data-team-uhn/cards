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
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;

/**
 * Basic utilities for working with Form data.
 *
 * @version $Id$
 */
@Component
public final class FormUtilsImpl extends AbstractNodeUtils implements FormUtils
{
    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private QuestionnaireUtils questionnaires;

    @Reference
    private SubjectUtils subjects;

    // Form methods

    @Override
    public boolean isForm(final Node node)
    {
        return isNodeType(node, FORM_NODETYPE);
    }

    @Override
    public boolean isForm(final NodeBuilder node)
    {
        return node == null ? false : isForm(node.getNodeState());
    }

    @Override
    public boolean isForm(final NodeState node)
    {
        return isNodeType(node, FORM_NODETYPE, getSession(this.rrf));
    }

    @Override
    public Node getQuestionnaire(final Node form)
    {
        return isForm(form) ? getReferencedNode(form, QUESTIONNAIRE_PROPERTY) : null;
    }

    @Override
    public Node getQuestionnaire(final NodeBuilder form)
    {
        return getQuestionnaire(form.getNodeState());
    }

    @Override
    public Node getQuestionnaire(final NodeState form)
    {
        return isForm(form) ? this.questionnaires.getQuestionnaire(getQuestionnaireIdentifier(form)) : null;
    }

    @Override
    public String getQuestionnaireIdentifier(final Node form)
    {
        return isForm(form) ? getStringProperty(form, QUESTIONNAIRE_PROPERTY) : null;
    }

    @Override
    public String getQuestionnaireIdentifier(final NodeBuilder form)
    {
        return getQuestionnaireIdentifier(form.getNodeState());
    }

    @Override
    public String getQuestionnaireIdentifier(final NodeState form)
    {
        return isForm(form) ? getStringProperty(form, QUESTIONNAIRE_PROPERTY) : null;
    }

    @Override
    public Node getSubject(final Node form)
    {
        return isForm(form) ? getReferencedNode(form, SUBJECT_PROPERTY) : null;
    }

    @Override
    public Node getSubject(final NodeBuilder form)
    {
        return getSubject(form.getNodeState());
    }

    @Override
    public Node getSubject(final NodeState form)
    {
        return isForm(form) ? this.subjects.getSubject(getSubjectIdentifier(form)) : null;
    }

    @Override
    public String getSubjectIdentifier(final Node form)
    {
        return isForm(form) ? getStringProperty(form, SUBJECT_PROPERTY) : null;
    }

    @Override
    public String getSubjectIdentifier(final NodeBuilder form)
    {
        return getSubjectIdentifier(form.getNodeState());
    }

    @Override
    public String getSubjectIdentifier(final NodeState form)
    {
        return isForm(form) ? getStringProperty(form, SUBJECT_PROPERTY) : null;
    }

    // AnswerSection methods

    @Override
    public boolean isAnswerSection(final Node node)
    {
        return isNodeType(node, ANSWER_SECTION_NODETYPE);
    }

    @Override
    public boolean isAnswerSection(final NodeState node)
    {
        return isNodeType(node, ANSWER_SECTION_NODETYPE, getSession(this.rrf));
    }

    @Override
    public boolean isAnswerSection(final NodeBuilder node)
    {
        return node == null ? false : isAnswerSection(node.getNodeState());
    }

    @Override
    public Node getSection(final Node answerSection)
    {
        return isAnswerSection(answerSection) ? getReferencedNode(answerSection, SECTION_PROPERTY) : null;
    }

    @Override
    public Node getSection(final NodeBuilder answerSection)
    {
        return getSection(answerSection.getNodeState());
    }

    @Override
    public Node getSection(final NodeState answerSection)
    {
        return this.questionnaires.getSection(getSectionIdentifier(answerSection));
    }

    @Override
    public String getSectionIdentifier(final Node answerSection)
    {
        return isAnswerSection(answerSection) ? getStringProperty(answerSection, SECTION_PROPERTY) : null;
    }

    @Override
    public String getSectionIdentifier(final NodeBuilder answerSection)
    {
        return getSectionIdentifier(answerSection.getNodeState());
    }

    @Override
    public String getSectionIdentifier(final NodeState answerSection)
    {
        return isAnswerSection(answerSection) ? getStringProperty(answerSection, SECTION_PROPERTY) : null;
    }

    // Answer methods

    @Override
    public boolean isAnswer(final Node node)
    {
        return isNodeType(node, ANSWER_NODETYPE);
    }

    @Override
    public boolean isAnswer(final NodeBuilder node)
    {
        return node == null ? false : isAnswer(node.getNodeState());
    }

    @Override
    public boolean isAnswer(final NodeState node)
    {
        return isNodeType(node, ANSWER_NODETYPE, getSession(this.rrf));
    }

    @Override
    public Node getQuestion(final Node answer)
    {
        return isAnswer(answer) ? getReferencedNode(answer, QUESTION_PROPERTY) : null;
    }

    @Override
    public Node getQuestion(final NodeBuilder answer)
    {
        return getQuestion(answer.getNodeState());
    }

    @Override
    public Node getQuestion(final NodeState answer)
    {
        return this.questionnaires.getQuestion(getQuestionIdentifier(answer));
    }

    @Override
    public String getQuestionIdentifier(final Node answer)
    {
        return isAnswer(answer) ? getStringProperty(answer, QUESTION_PROPERTY) : null;
    }

    @Override
    public String getQuestionIdentifier(final NodeBuilder answer)
    {
        return getQuestionIdentifier(answer.getNodeState());
    }

    @Override
    public String getQuestionIdentifier(final NodeState answer)
    {
        return isAnswer(answer) ? getStringProperty(answer, QUESTION_PROPERTY) : null;
    }

    @Override
    public Object getValue(final Node answer)
    {
        if (answer == null) {
            return null;
        }
        Object result = null;
        try {
            final Property value = answer.getProperty(VALUE_PROPERTY);
            if (value != null) {
                if (value.isMultiple()) {
                    final Value[] values = value.getValues();
                    result = new Object[values.length];
                    for (int i = 0; i < values.length; i++) {
                        ((Object[]) result)[i] = getValue(values[i]);
                    }
                } else {
                    result = getValue(value.getValue());
                }
            }
        } catch (final RepositoryException e) {
            // Shouldn't happen
        }
        return result;
    }

    @Override
    public Object getValue(final NodeBuilder answer)
    {
        return answer == null ? null : getValue(answer.getNodeState());
    }

    @Override
    public Object getValue(final NodeState answer)
    {
        if (answer == null) {
            return null;
        }
        Object result = null;
        final PropertyState valuePropertyState = answer.getProperty(VALUE_PROPERTY);
        if (valuePropertyState != null) {
            final Type<?> valueType = valuePropertyState.getType();

            if (valuePropertyState.isArray()) {
                result = new Object[valuePropertyState.count()];
                for (int i = 0; i < valuePropertyState.count(); i++) {
                    ((Object[]) result)[i] = valuePropertyState.getValue(valueType.getBaseType(), i);
                }
            } else {
                result = valuePropertyState.getValue(valueType);
            }
        }
        return result;
    }

    // Internal helper methods

    /**
     * Extract the actual value from a Value object.
     *
     * @param value a Value object
     * @return the actual value stored in the object
     */
    private Object getValue(final Value value)
    {
        Object result = null;
        try {
            switch (value.getType()) {
                case PropertyType.BOOLEAN:
                    result = value.getBoolean();
                    break;
                case PropertyType.DATE:
                    result = value.getDate();
                    break;
                case PropertyType.DECIMAL:
                    result = value.getDecimal();
                    break;
                case PropertyType.DOUBLE:
                    result = value.getDouble();
                    break;
                case PropertyType.LONG:
                    result = value.getLong();
                    break;
                default:
                    result = value.getString();
            }
        } catch (final RepositoryException e) {
            // Shouldn't happen
        }
        return result;
    }

    @Override
    public Node getAnswer(final Node form, final Node question)
    {
        try {
            if (isForm(form)) {
                return findNode(form, QUESTION_PROPERTY, question.getIdentifier());
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
        return null;
    }

    private Node findNode(final Node parent, final String property, final String value)
    {
        try {
            if (parent.hasProperty(property)
                && StringUtils.equals(parent.getProperty(property).getValue().getString(), value)) {
                return parent;
            }
            final NodeIterator children = parent.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                final Node result = findNode(child, property, value);
                if (result != null) {
                    return result;
                }
            }
        } catch (IllegalStateException | RepositoryException e) {
            // Not found or not accessible, just return null
        }
        return null;
    }

}
