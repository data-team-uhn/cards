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
package io.uhndata.cards.dataentry.api;

import javax.jcr.Node;

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
}
