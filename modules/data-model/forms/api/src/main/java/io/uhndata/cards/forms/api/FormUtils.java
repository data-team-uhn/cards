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
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

import jakarta.json.JsonValue;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /** The primary node type for the FormsHomepage, the parent of all forms. */
    String FORMS_HOMEPAGE_NODETYPE = "cards:FormsHomepage";

    /** The Sling resource type of the FormsHomepage. */
    String FORMS_HOMEPAGE_RESOURCE = "cards/FormsHomepage";

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

    /** The Sling resource super type of an AnswerSection. */
    String ANSWER_SECTION_SUPERTYPE = "cards/ResourcePart";

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

    /** The name of the property of a Form, Answer and AnswerSection node that holds its status flags. */
    String STATUS_FLAGS_PROPERTY = "statusFlags";

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
    boolean isForm(@Nullable Node node);

    /**
     * Check if the given node is a Form node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Form}, {@code false} otherwise
     */
    boolean isForm(@Nullable NodeBuilder node);

    /**
     * Check if the given node is a Form node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Form}, {@code false} otherwise
     */
    boolean isForm(@Nullable NodeState node);

    /**
     * Check if the given node is the FormsHomepage node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:FormsHomepage},
     *         {@code false} otherwise
     */
    boolean isFormsHomepage(@Nullable Node node);

    /**
     * Check if the given node is the FormsHomepage node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:FormsHomepage},
     *         {@code false} otherwise
     */
    boolean isFormsHomepage(@Nullable NodeBuilder node);

    /**
     * Check if the given node is the FormsHomepage node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:FormsHomepage},
     *         {@code false} otherwise
     */
    boolean isFormsHomepage(@Nullable NodeState node);

    /**
     * Look up the form that a node belongs to. The given node must be a descendant of a form node, i.e. an answer or
     * answer section, or it may be the form node itself.
     *
     * @param node a node descendant of a form node
     * @return the form node, an ancestor-or-self of the given node, or {@code null} if the given node doesn't have a
     *         form ancestor or if the form node is inaccessible to the current user
     */
    @Nullable
    Node getForm(@Nullable Node node);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getQuestionnaire(@Nullable Node form);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getQuestionnaire(@Nullable NodeBuilder form);

    /**
     * Retrieve the JCR node of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return a Questionnaire node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getQuestionnaire(@Nullable NodeState form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getQuestionnaireIdentifier(@Nullable Node form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getQuestionnaireIdentifier(@Nullable NodeBuilder form);

    /**
     * Retrieve the UUID of the Questionnaire that a Form node answers.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getQuestionnaireIdentifier(@Nullable NodeState form);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getSubject(@Nullable Node form);

    /**
     * Retrieve the JCR node of the Subject that a Form relates to.
     *
     * @param form a Form node, may be {@code null}
     * @param subjectTypePath the path to a subject type, e.g. {@code /SubjectTypes/Patient}
     * @return a Subject node, or {@code null} if the provided node is not a Form or no subject of the given type is
     *         related to the form
     */
    @Nullable
    Node getSubject(@Nullable Node form, @NotNull String subjectTypePath);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getSubject(@Nullable NodeBuilder form);

    /**
     * Retrieve the JCR node of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return a Subject node, or {@code null} if the provided node is not a Form
     */
    @Nullable
    Node getSubject(@Nullable NodeState form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getSubjectIdentifier(@Nullable Node form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getSubjectIdentifier(@Nullable NodeBuilder form);

    /**
     * Retrieve the UUID of the Subject that a Form belongs to.
     *
     * @param form a Form node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not a Form
     */
    @Nullable
    String getSubjectIdentifier(@Nullable NodeState form);

    // AnswerSection methods

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a JCR node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(@Nullable Node node);

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(@Nullable NodeBuilder node);

    /**
     * Check if the given node is an Answer Section node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:AnswerSection}, {@code false}
     *         otherwise
     */
    boolean isAnswerSection(@Nullable NodeState node);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    Node getSection(@Nullable Node answerSection);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    Node getSection(@Nullable NodeBuilder answerSection);

    /**
     * Retrieve the JCR node of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return a Section node, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    Node getSection(@Nullable NodeState answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    String getSectionIdentifier(@Nullable Node answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    String getSectionIdentifier(@Nullable NodeBuilder answerSection);

    /**
     * Retrieve the UUID of the Section that an Answer Section node answers.
     *
     * @param answerSection an Answer Section node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an Answer Section
     */
    @Nullable
    String getSectionIdentifier(@Nullable NodeState answerSection);

    // Answer methods

    /**
     * Get the first answer for a specific question, if any.
     *
     * @param form a Form node
     * @param question a question node, part of the questionnaire that the form is answering
     * @return an Answer node, may be {@code null}
     */
    @Nullable
    Node getAnswer(@Nullable Node form, @Nullable Node question);

    /**
     * Get the first answer for a specific question, if any.
     *
     * @param form a Form node
     * @param question a question node, part of the questionnaire that the form is answering
     * @return an Answer node, may be {@code null}
     */
    @Nullable
    NodeState getAnswer(@Nullable NodeState form, @Nullable Node question);

    /**
     * Get all the answers for a specific question, if any.
     *
     * @param form a Form node
     * @param question a question node, part of the questionnaire that the form is answering
     * @return a series of Answer nodes, may be empty list
     */
    @NotNull
    Collection<Node> getAllAnswers(@NotNull Node form, @NotNull Node question);

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
    @NotNull
    Collection<Node> findAllFormRelatedAnswers(@NotNull Node startingForm, @NotNull Node question,
        @NotNull EnumSet<SearchType> scope);

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
    @NotNull
    Collection<Node> findAllSubjectRelatedAnswers(@NotNull Node startingSubject, @NotNull Node question,
        @NotNull EnumSet<SearchType> scope);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a JCR node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(@Nullable Node node);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a node builder, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(@Nullable NodeBuilder node);

    /**
     * Check if the given node is an Answer node.
     *
     * @param node the node to check, a node state, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Answer}, {@code false} otherwise
     */
    boolean isAnswer(@Nullable NodeState node);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    @Nullable
    Node getQuestion(@Nullable Node answer);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    @Nullable
    Node getQuestion(@Nullable NodeBuilder answer);

    /**
     * Retrieve the JCR node of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return a Question node, or {@code null} if the provided node is not an answer
     */
    @Nullable
    Node getQuestion(@Nullable NodeState answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    @Nullable
    String getQuestionIdentifier(@Nullable Node answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    @Nullable
    String getQuestionIdentifier(@Nullable NodeBuilder answer);

    /**
     * Retrieve the UUID of the Question that an Answer node answers.
     *
     * @param answer an Answer node, may be {@code null}
     * @return an identifier, or {@code null} if the provided node is not an answer
     */
    @Nullable
    String getQuestionIdentifier(@Nullable NodeState answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    @Nullable
    Object getValue(@Nullable Node answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    @Nullable
    Object getValue(@Nullable NodeBuilder answer);

    /**
     * Retrieve the value(s) stored in an Answer.
     *
     * @param answer an Answer node, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    @Nullable
    Object getValue(@Nullable NodeState answer);

    /**
     * Retrieve the value(s) stored in a Property.
     *
     * @param value a Property value, may be {@code null}
     * @return the value or values stored in the answer, either as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String), or an array of simple values; reference and path values are returned
     *         as strings (UUID or path), and not as the referenced nodes themselves; {@code null} may be returned if no
     *         value is stored in the answer
     */
    @Nullable
    Object getValue(@Nullable Property value);

    /**
     * Extract the actual value from a Value object.
     *
     * @param value a Value object, may be {@code null}
     * @return the actual value stored in the object as a simple value of the corresponding type (e.g.
     *         Boolean, Calendar, Decimal, String); {@code null} may be returned if no value is stored in the answer
     */
    @Nullable
    Object getValue(@Nullable Value value);

    /**
     * Extract the set of status flags from a form, answer section or answer node.
     *
     * @param node the form, answer section or answer node to pull the status flags from
     * @return the set of status flags for a form.
     *         If no status flags are present, an empty set will be returned.
     *         {@code null} may be returned if the node is not a supported type
     */
    @Nullable
    Set<String> getStatusFlags(@Nullable Node node);

    /**
     * Serialize the value(s) stored in an Answer.
     *
     * @param property a property of Answer node, may be {@code null}
     * @return a {@code JsonValue} holding the value or values stored in the answer; this may be a single value or an
     *         array of values, or {@code JsonValue.NULL} if no value is stored in the answer or in case of exception
     *         caught in the serialization process
     */
    @NotNull
    JsonValue serializeProperty(@Nullable Property property);
}
