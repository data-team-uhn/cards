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

import javax.jcr.Node;

/**
 * Basic utilities for working with Questionnaires.
 *
 * @version $Id$
 */
public interface QuestionnaireUtils
{
    /** The primary node type for a Questionnaire, the definition of a type of forms to be filled in. */
    String QUESTIONNAIRE_NODETYPE = "cards:Questionnaire";

    /** The Sling resource type for a Questionnaire. */
    String QUESTIONNAIRE_RESOURCE = "cards/Questionnaire";

    /** The primary node type for a Section, a group of related questions and subsections in a Questionnaire. */
    String SECTION_NODETYPE = "cards:Section";

    /** The Sling resource type for a Section. */
    String SECTION_RESOURCE = "cards/Section";

    /**
     * The primary node type for an Information. Information nodes don't need to be answered, they just present extra
     * information to the user, for example details about how to fill in the form.
     */
    String INFORMATION_NODETYPE = "cards:Information";

    /** The Sling resource type for an Information. */
    String INFORMATION_RESOURCE = "cards/Information";

    /** The property of an Information node specifying in which modes it should be displayed: view, edit, print... */
    String INFORMATION_DISPLAY_MODE_PROPERTY = "formMode";

    /** The primary node type for a Question. */
    String QUESTION_NODETYPE = "cards:Question";

    /** The Sling resource type for a Question. */
    String QUESTION_RESOURCE = "cards/Question";

    /** The primary node type for an Answer Option, a predefined answer for a question that the user may choose. */
    String ANSWER_OPTION_NODETYPE = "cards:AnswerOption";

    /** The Sling resource type for an Answer Option. */
    String ANSWER_OPTION_RESOURCE = "cards/AnswerOption";

    /**
     * Check if the given node is a Questionnaire node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Questionnaire}, {@code false}
     *         otherwise
     */
    boolean isQuestionnaire(Node node);

    /**
     * Retrieve the Questionnaire with the given UUID.
     *
     * @param identifier an UUID that references a questionnaire.
     * @return a Node
     */
    Node getQuestionnaire(String identifier);

    /**
     * Check if the given node is a Section node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Section}, {@code false}
     *         otherwise
     */
    boolean isSection(Node node);

    /**
     * Check if the given node is a conditional Section node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Section} and has a condition,
     *         {@code false} otherwise
     */
    boolean isConditionalSection(Node node);

    /**
     * Retrieve the Section with the given UUID.
     *
     * @param identifier an UUID that references a section.
     * @return a Node
     */
    Node getSection(String identifier);

    /**
     * Get a Question from a Questionnaire.
     *
     * @param questionnaire a Questionnaire node
     * @param relativePath relative path from the Questionnaire node to a Question
     * @return a Question node, may be {@code null}
     */
    Node getQuestion(Node questionnaire, String relativePath);

    /**
     * Check if the given node is a Question node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Question}, {@code false}
     *         otherwise
     */
    boolean isQuestion(Node node);

    /**
     * Check if the given node is a Question node for a computed question.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null}, is computed and is of type {@code cards:Question},
     *         {@code false} otherwise
     */
    boolean isComputedQuestion(Node node);

    /**
     * Check if the given node is a Question node for a reference question.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null}, is of type {@code cards:Question} and is a reference,
     *         {@code false} otherwise
     */
    boolean isReferenceQuestion(Node node);

    /**
     * Retrieve the Question with the given UUID.
     *
     * @param identifier an UUID that references a question.
     * @return a Node, or {@code null} if the question could not be found
     */
    Node getQuestion(String identifier);

    /**
     * Check if a node is an element of the given questionnaire, i.e. a question or section that is part of the
     * questionnaire.
     *
     * @param element a node to check
     * @param questionnaire the questionnaire that should be an ancestor of the element
     * @return {@code true} if the element does belong to the questionnaire, {@code false} if either element is
     *         {@code null}, or not an expected node type
     */
    boolean belongs(Node element, Node questionnaire);

    /**
     * Return the questionnaire that owns the provided element, if any.
     *
     * @param element a node that belongs to a questionnaire, e.g. a Question or Section node
     * @return a questionnaire node, or {@code null}
     */
    Node getOwnerQuestionnaire(Node element);

    /**
     * Retrieve the name of a question, a short internal name.
     *
     * @param question a {@code cards:Question} node
     * @return the question's name, or {@code null} if the node is not a Question
     */
    String getQuestionName(Node question);

    /**
     * Retrieve the text of a question, the main text displayed to the user.
     *
     * @param question a {@code cards:Question} node
     * @return the question's text, or an empty string if no text is present
     */
    String getQuestionText(Node question);

    /**
     * Retrieve the description of a question, an optional longer explanation/description for the question.
     *
     * @param question a {@code cards:Question} node
     * @return the question's description, or an empty string if no description is present
     */
    String getQuestionDescription(Node question);
}
