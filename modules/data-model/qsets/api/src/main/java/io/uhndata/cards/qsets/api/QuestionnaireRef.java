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

import javax.jcr.Node;

/**
 * Represents a reference to a questionnaire contained in a {@link QuestionnaireSet}. Other than the link to an actual
 * {@code cards:Questionnaire} node, it also specifies how often this questionnaire should be completed.
 *
 * @version $Id$
 * @since 0.9.25
 */
public interface QuestionnaireRef
{
    // Constants for the QuestionnaireRef data type

    /** The primary node type for a Questionnaire Reference. */
    String NODETYPE = "cards:QuestionnaireRef";

    /** The name of the property of a QuestionnaireRef node that links to the target Questionnaire. */
    String QUESTIONNAIRE_PROPERTY = "questionnaire";

    /** The name of the property of a QuestionnaireRef node that specifies the frequency. */
    String FREQUENCY_PROPERTY = "frequency";

    /**
     * Get the path to the target questionnaire.
     *
     * @return a path to a JCR questionnaire node
     */
    String getQuestionnairePath();

    /**
     * The target questionnaire.
     *
     * @return a JCR Node
     */
    Node getQuestionnaire();

    /**
     * How often can this questionnaire be completed.
     *
     * @return a number of weeks
     */
    long getFrequency();
}
