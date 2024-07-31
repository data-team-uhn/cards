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
package io.uhndata.cards.patients.api;

import java.util.Calendar;

import javax.jcr.Node;

import io.uhndata.cards.qsets.api.QuestionnaireSet;

/**
 * Represents information about a visit.
 *
 * @version $Id$
 * @since 0.9.25
 */
public interface VisitInformation
{
    /**
     * Retrieve the Visit Information form for the visit.
     *
     * @return a {@code cards:Form} JCR node
     */
    Node getVisitInformationForm();

    /**
     * Check if all the mandatory information is present for this visit. At the moment, this just means a visit date and
     * a questionnaire set.
     *
     * @return {@code true} if all required information is present, {@code false otherwise}
     */
    boolean hasRequiredInformation();

    /**
     * Check if all the patient-facing forms for this visit are complete.
     *
     * @return {@code true} if all patient-facing forms are complete, {@code false} otherwise
     */
    boolean isComplete();

    /**
     * Check if the patient submitted their forms.
     *
     * @return {@code true} if the visit is marked as submitted, {@code false} otherwise
     */
    boolean isSubmitted();

    /**
     * Retrieve the date for this visit.
     *
     * @return a date, may be {@code null}
     */
    Calendar getVisitDate();

    /**
     * Retrieve the short name of the associated questionnaire set.
     *
     * @return a short name
     */
    String getQuestionnaireSetName();

    /**
     * Retrieve the list of forms expected for a visit for the associated questionnaire set. Not all of these forms
     * exist or should exist for this visit, this is just the definition in the questionnaire set.
     *
     * @return a questionnaire set with all the questionnaires specified in the questionnaire set definition, may be
     *         empty
     */
    QuestionnaireSet getTemplateForms();

    /**
     * Retrieve the list of forms that actually exist for this visit.
     *
     * @return a questionnaire set with all the existing forms for this visit, may be empty
     */
    QuestionnaireSet getExistingForms();

    /**
     * Retrieve the list of forms that must be created for this visit. Starting from the questionnaire set definition,
     * this doesn't include already existing forms and forms that don't need to be created because of their expected
     * frequency and other past forms in the system.
     *
     * @return a questionnaire set with the questionnaires that must be instantiated for this visit, may be empty
     */
    QuestionnaireSet getMissingForms();

    /**
     * Retrieve the path to the clinic associated with this visit.
     *
     * @return a path to a JCR node of type {@code cards:ClinicMapping}, may be {@code null}
     */
    String getClinicPath();
}
