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
package io.uhndata.cards.qsets.api;

import java.util.Calendar;

import javax.jcr.Node;

/**
 * Utility service for working with questionnaire sets.
 *
 * @version $Id$
 * @since 0.9.25
 */
public interface QuestionnaireSetUtils
{
    /**
     * Return a {@link QuestionnaireConflict} object reflecting the provided {@code cards:QuestionnaireConflict} JCR
     * node.
     *
     * @param definition a JCR node of type {@code cards:QuestionnaireConflict}
     * @return the parsed object, or {@code null} if the node is not of the right type or accessing it fails
     */
    QuestionnaireConflict toQuestionnaireConflict(Node definition);

    /**
     * Return a {@link QuestionnaireRef} object reflecting the provided {@code cards:QuestionnaireRef} JCR node.
     *
     * @param definition a JCR node of type {@code cards:QuestionnaireRef}
     * @return the parsed object, or {@code null} if the node is not of the right type or accessing it fails
     */
    QuestionnaireRef toQuestionnaireRef(Node definition);

    /**
     * Return a {@link QuestionnaireRef} object explicitly referencing a target {@code cards:Questionnaire} node.
     *
     * @param questionnaire a JCR node of type {@code cards:Questionnaire}
     * @param frequency the frequency to use
     * @return the requested object
     */
    QuestionnaireRef toQuestionnaireRef(Node questionnaire, long frequency);

    /**
     * Return a {@link QuestionnaireSet} object reflecting the provided {@code cards:QuestionnaireSet} JCR node and the
     * provided date.
     *
     * @param definition a JCR node of type {@code cards:QuestionnaireSet}
     * @param associatedDate the date to associate with the set
     * @return the parsed object, or {@code null} if the node is not of the right type or accessing it fails
     */
    QuestionnaireSet toQuestionnaireSet(Node definition, Calendar associatedDate);

    /**
     * Return a {@link QuestionnaireSet} without a template, just the provided date. Member Questionnaires (or
     * conflicts) need to be added manually to the set.
     *
     * @param associatedDate the date to associate with the set
     * @return an empty QuestionnaireSet with just an associated date
     */
    QuestionnaireSet toQuestionnaireSet(Calendar associatedDate);

    /**
     * Return a modifiable copy of the given {@link QuestionnaireSet} object.
     *
     * @param toCopy the {@link QuestionnaireSet} object to copy
     * @return a copy
     */
    QuestionnaireSet copy(QuestionnaireSet toCopy);
}
