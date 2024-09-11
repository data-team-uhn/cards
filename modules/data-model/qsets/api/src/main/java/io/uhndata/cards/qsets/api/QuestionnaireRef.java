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

import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a reference to a questionnaire contained in a {@link QuestionnaireSet}. Other than the link to an actual
 * {@code cards:Questionnaire} node, it also specifies how often this questionnaire should be completed, and whether it
 * should be completed by the patient or by a clinician.
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

    /** The name of the property of a QuestionnaireRef node that specifies the target user type. */
    String TARGET_USER_TYPE_PROPERTY = "targetUserType";

    /**
     * Which type of user should fill in this questionnaire.
     */
    enum TargetUserType
    {
        /** The patient should fill in this questionnaire. */
        PATIENT,
        /** The clinician should fill in this questionnaire. */
        CLINICIAN,
        /** Anybody can fill in this questionnaire, either the patient or the clinician. */
        ANY;

        private static final Logger LOGGER = LoggerFactory.getLogger(TargetUserType.class);

        /**
         * Extract the target user type specified in the given node.
         *
         * @param definition a JCR node of type {@code cards:QuestionnaireRef}
         * @return a target user type, {@link ANY} if not specified in the node
         */
        public static TargetUserType valueOf(Node definition)
        {
            try {
                if (definition != null && definition.hasProperty(TARGET_USER_TYPE_PROPERTY)) {
                    return valueOf(
                        definition.getProperty(TARGET_USER_TYPE_PROPERTY).getString().toUpperCase(Locale.ROOT));
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid target user type for {}", definition);
            }
            return ANY;
        }
    }

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
     * Who should fill in this questionnaire.
     *
     * @return a target type
     */
    TargetUserType getTargetUserType();

    /**
     * Check if a patient can/should fill in this questionnaire.
     *
     * @return {@code true} if the target user type is either explicitly a patient, or any type of user
     */
    default boolean isPatientFacing()
    {
        return getTargetUserType() == TargetUserType.PATIENT || getTargetUserType() == TargetUserType.ANY;
    }

    /**
     * Check if a clinician can/should fill in this questionnaire.
     *
     * @return {@code true} if the target user type is either explicitly a clinician, or any type of user
     */
    default boolean isClinicianFacing()
    {
        return getTargetUserType() == TargetUserType.CLINICIAN || getTargetUserType() == TargetUserType.ANY;
    }

    /**
     * How often can this questionnaire be completed.
     *
     * @return a number of weeks
     */
    long getFrequency();
}
