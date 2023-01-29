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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Basic utilities for working with Form data.
 *
 * @version $Id$
 */
@Component
public final class FormUtilsImpl extends AbstractNodeUtils implements FormUtils
{
    @Reference
    private ThreadResourceResolverProvider rrp;

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
        return isNodeType(node, FORM_NODETYPE, getSession(this.rrp));
    }

    @Override
    public Node getForm(final Node answer)
    {
        try {
            Node parent = answer;
            while (parent != null && !isForm(parent)) {
                parent = parent.getParent();
            }
            return parent;
        } catch (RepositoryException e) {
            // Not expected, or the session user doesn't have access to it
        }
        return null;
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
    public Node getSubject(final Node form, final String subjectTypePath)
    {
        return isForm(form)
            ? getReferencedNodeOfType(form, RELATED_SUBJECTS_PROPERTY, subjectTypePath, SubjectUtils.TYPE_PROPERTY)
            : null;
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
        return isNodeType(node, ANSWER_SECTION_NODETYPE, getSession(this.rrp));
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
        return isNodeType(node, ANSWER_NODETYPE, getSession(this.rrp));
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
                case PropertyType.BINARY:
                    // We're supposed to be transforming raw bytes into an Unicode string;
                    // ISO 8859-1 is a subset of Unicode
                    result = IOUtils.toString(value.getBinary().getStream(), StandardCharsets.ISO_8859_1);
                    break;
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
        } catch (final RepositoryException | IOException e) {
            // Shouldn't happen
        }
        return result;
    }

    @Override
    public Node getAnswer(final Node form, final Node question)
    {
        try {
            if (isForm(form) && this.questionnaires.isQuestion(question)) {
                return findNode(form, QUESTION_PROPERTY, question.getIdentifier());
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
        return null;
    }

    @Override
    public Collection<Node> getAllAnswers(final Node form, final Node question)
    {
        return findAllFormRelatedAnswers(form, question, EnumSet.of(SearchType.FORM));
    }

    @Override
    public Collection<Node> findAllFormRelatedAnswers(final Node startingForm, final Node question,
        final EnumSet<SearchType> scope)
    {
        final Map<String, Node> result = new LinkedHashMap<>();
        final Consumer<Node> consumer = node -> {
            try {
                result.put(node.getIdentifier(), node);
            } catch (final RepositoryException e) {
                // Ignore
            }
        };
        final Node targetQuestionnaire = this.questionnaires.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return result.values();
        }
        try {
            if (scope.contains(SearchType.FORM)) {
                findAnswersInForm(startingForm, question, consumer);
            }
            final Node subject = getSubject(startingForm);
            if (scope.contains(SearchType.SUBJECT_FORMS)) {
                findAnswersInSubject(subject, question, scope, consumer);
            }
            if (scope.contains(SearchType.DESCENDANTS_FORMS)) {
                findAnswersInDescendants(subject, question, consumer);
            }
            if (scope.contains(SearchType.ANCESTORS_FORMS)) {
                findAnswersInAncestors(subject, question, scope, consumer);
            }
        } catch (final RepositoryException e) {
            // Not expected
        }
        return result.values();
    }

    @Override
    public Collection<Node> findAllSubjectRelatedAnswers(final Node startingSubject, final Node question,
        final EnumSet<SearchType> scope)
    {
        final Map<String, Node> result = new LinkedHashMap<>();
        final Consumer<Node> consumer = node -> {
            try {
                result.put(node.getIdentifier(), node);
            } catch (final RepositoryException e) {
                // Ignore
            }
        };
        final Node targetQuestionnaire = this.questionnaires.getOwnerQuestionnaire(question);
        if (targetQuestionnaire == null) {
            return result.values();
        }
        try {
            if (scope.contains(SearchType.SUBJECT_FORMS)) {
                findAnswersInSubject(startingSubject, question, scope, consumer);
            }
            if (scope.contains(SearchType.DESCENDANTS_FORMS)) {
                findAnswersInDescendants(startingSubject, question, consumer);
            }
            if (scope.contains(SearchType.ANCESTORS_FORMS)) {
                findAnswersInAncestors(startingSubject, question, scope, consumer);
            }
        } catch (final RepositoryException e) {
            // Not expected
        }
        return result.values();
    }

    @Override
    public JsonValue serializeProperty(final Property property)
    {
        if (property == null) {
            return JsonValue.NULL;
        }
        try {
            if (property.isMultiple()) {
                return serializeMultiValuedProperty(property);
            } else {
                return serializeSingleValuedProperty(property);
            }
        } catch (RepositoryException e) {
            // Not found or not accessible, just return null
        }
        return JsonValue.NULL;
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

    private void findAllNodes(final Node parent, final String property, final String value, final Consumer<Node> result)
    {
        try {
            if (parent.hasProperty(property)
                && StringUtils.equals(parent.getProperty(property).getValue().getString(), value)) {
                result.accept(parent);
            }
            final NodeIterator children = parent.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                findAllNodes(child, property, value, result);
            }
        } catch (IllegalStateException | RepositoryException e) {
            // Not found or not accessible, just return null
        }
    }

    private void findAnswersInForm(final Node startingForm, final Node question, final Consumer<Node> result)
        throws RepositoryException
    {
        findAllNodes(startingForm, QUESTION_PROPERTY, question.getIdentifier(), result);
    }

    private void findAnswersInSubject(final Node subject, final Node question, final EnumSet<SearchType> scope,
        final Consumer<Node> result) throws RepositoryException
    {
        if (this.subjects.isSubject(subject)) {
            findAnswersInSubjectForms(subject, question, result);
        }
    }

    private void findAnswersInAncestors(final Node startingSubject, final Node question,
        final EnumSet<SearchType> scope, final Consumer<Node> result) throws RepositoryException
    {
        Node nextSubject = startingSubject.getParent();
        // We stop when we've reached the top of the subjects hierarchy
        while (this.subjects.isSubject(nextSubject)) {
            findAnswersInSubjectForms(nextSubject, question, result);
            nextSubject = nextSubject.getParent();
        }
    }

    private void findAnswersInDescendants(final Node startingSubject, final Node question, final Consumer<Node> result)
        throws RepositoryException
    {
        Queue<Node> descendants = new LinkedList<>();
        NodeIterator children = startingSubject.getNodes();
        while (children.hasNext()) {
            descendants.add(children.nextNode());
        }
        // BFS search in all descendant subjects
        while (!descendants.isEmpty()) {
            final Node nextSubject = descendants.poll();
            if (this.subjects.isSubject(nextSubject)) {
                // Look for answers for this subject
                findAnswersInSubjectForms(nextSubject, question, result);
                // Add the child nodes next in queue
                children = nextSubject.getNodes();
                while (children.hasNext()) {
                    descendants.add(children.nextNode());
                }
            }
        }
    }

    private void findAnswersInSubjectForms(final Node subject, final Node question, final Consumer<Node> result)
        throws RepositoryException
    {
        final Node targetQuestionnaire = this.questionnaires.getOwnerQuestionnaire(question);
        // Look for a form answering the right questionnaire for this subject
        final PropertyIterator otherForms = subject.getReferences(SUBJECT_PROPERTY);
        while (otherForms.hasNext()) {
            final Node otherForm = otherForms.nextProperty().getParent();
            if (targetQuestionnaire.isSame(getQuestionnaire(otherForm))) {
                findAnswersInForm(otherForm, question, result);
            }
        }
    }

    private JsonValue serializeSingleValuedProperty(final Property property)
        throws RepositoryException
    {
        final Value value = property.getValue();
        JsonValue result = null;

        switch (property.getType()) {
            case PropertyType.BINARY:
                result = serializeInputStream(value.getBinary().getStream());
                break;
            case PropertyType.BOOLEAN:
                result = value.getBoolean() ? JsonValue.TRUE : JsonValue.FALSE;
                break;
            case PropertyType.DATE:
                result = serializeDate(value.getDate());
                break;
            case PropertyType.DOUBLE:
                result = Json.createValue(value.getDouble());
                break;
            case PropertyType.LONG:
                result = Json.createValue(value.getLong());
                break;
            case PropertyType.DECIMAL:
                // Send as string to prevent losing precision
            default:
                result = Json.createValue(value.getString());
                break;
        }
        return result;
    }

    private JsonValue serializeMultiValuedProperty(final Property property)
        throws RepositoryException
    {
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

        for (Value value : property.getValues()) {
            switch (property.getType()) {
                case PropertyType.BINARY:
                    arrayBuilder.add(serializeInputStream(value.getBinary().getStream()));
                    break;
                case PropertyType.BOOLEAN:
                    arrayBuilder.add(value.getBoolean());
                    break;
                case PropertyType.DATE:
                    arrayBuilder.add(serializeDate(value.getDate()));
                    break;
                case PropertyType.DOUBLE:
                    arrayBuilder.add(value.getDouble());
                    break;
                case PropertyType.LONG:
                    arrayBuilder.add(value.getLong());
                    break;
                case PropertyType.DECIMAL:
                    // Send decimals as string to prevent losing precision
                default:
                    arrayBuilder.add(value.getString());
                    break;
            }
        }
        return arrayBuilder.build();
    }

    private JsonValue serializeDate(final Calendar value)
    {
        // Use the ISO 8601 date+time format
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(value.getTimeZone());
        return Json.createValue(sdf.format(value.getTime()));
    }

    private JsonValue serializeInputStream(final InputStream value)
    {
        try {
            // We're supposed to be transforming raw bytes into an Unicode string; ISO 8859-1 is a subset of Unicode
            return Json.createValue(IOUtils.toString(value, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            // return null
        }
        return JsonValue.NULL;
    }
}
