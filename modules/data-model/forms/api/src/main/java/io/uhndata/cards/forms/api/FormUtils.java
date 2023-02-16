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
package io.uhndata.cards.forms.api;

import java.util.Collection;
import java.util.EnumSet;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.json.JsonValue;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * Basic utilities for working with Form data.
 *
 * @version $Id$
 */
public interface FormUtils
{
    // Constants for the Form data types

    /** The primary node type for a Form, a filled in questionnaire. */
    String FORM_NODETYPE = "cards:Form";

    /** The Sling resource type of a Form. */
    String FORM_RESOURCE = "cards/Form";

    /** The name of the property of a Form node that links to the Questionnaire being answered. */
    String QUESTIONNAIRE_PROPERTY = "questionnaire";

    /** The name of the property of a Form node that links to the Subject the form belongs to. */
    String SUBJECT_PROPERTY = "subject";

    /** The name of the property of a Form node that links to other Subjects the form relates to. */
    String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";

    /**
     * The primary node type for an Answer Section, a group of related answers and subsections in a Form, corresponding
     * to a Section in the answered Questionnaire.
     */
    String ANSWER_SECTION_NODETYPE = "cards:AnswerSection";

    /** The Sling resource type of an AnswerSection. */
    String ANSWER_SECTION_RESOURCE = "cards/AnswerSection";

    /** The name of the property of an AnswerSection node that links to the Section being answered. */
    String SECTION_PROPERTY = "section";

    /**
     * The node type for an Answer, a filled in value for a Question in the Questionnaire. This is usually not the
     * primary node type, since that depends on the type of answer (Boolean, Text, Decimal...), so this is usually the
     * supertype of an answer node.
     */
    String ANSWER_NODETYPE = "cards:Answer";

    /** The Sling resource type of an Answer. */
    String ANSWER_RESOURCE = "cards/Answer";

    /** The name of the property of an Answer node that links to the Question being answered. */
    String QUESTION_PROPERTY = "question";

    /** The name of the property of an Answer node that holds the actual value. */
    String VALUE_PROPERTY = "value";

    enum SearchType
    {
        /** Search only in this form. */
        FORM,
        /** Search in all the forms belonging to the subject. */
        SUBJECT_FORMS,
        /** Search in all the forms belonging to the subject and all ancestor subjects. */
        ANCESTORS_FORMS,
        /** Search in all the forms belonging to the subject and all descendant subjects. */
        DESCENDANTS_FORMS
    }

    // Form methods

    /**
     * Check if the given node is a Form node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Form}, {@code false} otherwise
     */
    boolean isForm(Node node);

    /**
     * Check if the given node is a Form node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Form}, {@code false} otherwise
     */
    boolean isForm(NodeBuilder node);

    /**
     * Check if the given node is a Form node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Form}, {@code false} otherwise
     */
    boolean isForm(NodeState node);

    /**
     * Look up the form that a node belongs to. The given node must be a descendant of a form node, i.e. an answer or
     * answer section, or it may be the form node itself.
     *
     * @param node a node descendant of a form node
     * @return the form node, an ancestor-or-self of the given node, or {@code null} if the given node doesn't have a
     *         form ancestor or if the form node is inaccessible to the current user
     */
    Node getForm(Node node);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    Node getQuestionnaire(Node form);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    Node getQuestionnaire(NodeBuilder form);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    Node getQuestionnaire(NodeState form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getQuestionnaireIdentifier(Node form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getQuestionnaireIdentifier(NodeBuilder form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getQuestionnaireIdentifier(NodeState form);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    Node getSubject(Node form);

    /**
     * Retrieve the JCR node of the Subject that a Form relates to.
     *
     * @param form a Form node, may be {@code null}
     * @param subjectTypePath the path to a subject type, e.g. {@code /SubjectTypes/Patient}
     * @return a Subject node, or {@code null} if the provided node is not a Form or no subject of the given type is
     *         related to the form
     */
    Node getSubject(Node form, String subjectTypePath);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    Node getSubject(NodeBuilder form);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    Node getSubject(NodeState form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getSubjectIdentifier(Node form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getSubjectIdentifier(NodeBuilder form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    String getSubjectIdentifier(NodeState form);

    // AnswerSection methods

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a JCR node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(Node node);

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(NodeBuilder node);

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(NodeState node);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    Node getSection(Node answerSection);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    Node getSection(NodeBuilder answerSection);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    Node getSection(NodeState answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    String getSectionIdentifier(Node answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    String getSectionIdentifier(NodeBuilder answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    String getSectionIdentifier(NodeState answerSection);

    // Answer methods

    /**
     * Get the first answer for a specific question, if any.
     *
     * @param form a Form node
     * @param question a question node, part of the questionnaire that the form is answering
     * @return an Answer node, may be {@code null}
     */
    Node getAnswer(Node form, Node question);

    /**
     * Get all the answers for a specific question, if any.
     *
     * @param form a Form node
     * @param question a question node, part of the questionnaire that the form is answering
     * @return a series of Answer nodes, may be empty list
     */
    Collection<Node> getAllAnswers(Node form, Node question);

    /**
     * Get all the answers for a specific question related to a form. This may be answers in the form itself, or answers
     * in other forms belonging to the same subject or a related subject.
     *
     * @param startingForm the form which is the basis of the answer search, does not need to be answering the same
     *            questionnaire as the target question
     * @param question the question being answered
     * @param scope where to search for answers
     * @return a collection of answers, may be empty if no answers are found, containing answers sorter by how close to
     *         the target form they are: first answers from the form, then from its subject's other forms, then
     *         descendant subjects, then ancestor subjects
     */
    Collection<Node> findAllFormRelatedAnswers(Node startingForm, Node question, EnumSet<SearchType> scope);

    /**
     * Get all the answers for a specific question related to a subject. This may be answers for the subject itself, or
     * answers in descendant or ancestor subjects.
     *
     * @param startingSubject the subject which is the basis of the answer search
     * @param question the question being answered
     * @param scope where to search for answers
     * @return a collection of answers, may be empty if no answers are found, containing answers sorter by how close to
     *         the target subject they are: first answers from the subject's own forms, then descendant subjects, then
     *         ancestor subjects
     */
    Collection<Node> findAllSubjectRelatedAnswers(Node startingSubject, Node question, EnumSet<SearchType> scope);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a JCR node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(Node node);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(NodeBuilder node);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(NodeState node);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    Node getQuestion(Node answer);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    Node getQuestion(NodeBuilder answer);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    Node getQuestion(NodeState answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    String getQuestionIdentifier(Node answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    String getQuestionIdentifier(NodeBuilder answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    String getQuestionIdentifier(NodeState answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    Object getValue(Node answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    Object getValue(NodeBuilder answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    Object getValue(NodeState answer);

    /**
     * Serialize the value(s) stored in an Answer.
     *
     * @param property a property of Answer node, may be {@code null}
     * @return a {@code JsonValue} holding the value or values stored in the answer; this may be a single value or an
     *         array of values, or {@code JsonValue.NULL} if no value is stored in the answer or in case of exception
     *         caught in the serialization process
     */
    JsonValue serializeProperty(Property property);
}
