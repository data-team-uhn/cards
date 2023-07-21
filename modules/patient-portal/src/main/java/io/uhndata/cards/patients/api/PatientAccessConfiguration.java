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

import javax.jcr.Node;

/**
 * Configuration for how the patient can access the patient-facing UI.
 *
 * @version $Id$
 */
public interface PatientAccessConfiguration
{
    /**
     * Check if tokenless authentication is enabled.
     *
     * @return True if tokenless authentication is enabled, {@code false} if the configuration could not be found or
     *         it is disabled
     */
    boolean isTokenlessAuthEnabled();

    /**
     * Check if patient self-identification is required.
     *
     * @return True if patient identification is required or if the configuration could not be found,
     *         {@code false} otherwise
     */
    boolean isPatientIdentificationRequired();

    /**
     * Get the configured amount of time, in days, after an appointment ends that the patient can still fill in the
     * visit surveys. {@code -1} means the patient can fill in the forms until the midnight right before the visit,
     * {@code 0} means until the midnight right after the visit, {@code 7} means until one week after the visit.
     *
     * @return A number of days
     */
    int getAllowedPostVisitCompletionTime();

    /**
     * Returns the token lifetime associated with the clinic linked to the Subject
     * related to the visitInformationNode Resource or default if it cannot be found.
     *
     * @param visitInformationForm the JCR Visit Information Node
     *
     * @return A number of days
     */
    int getAllowedPostVisitCompletionTime(Node visitInformationForm);

    /**
     * Get the configured amount of time, in days, that patient's draft responses are kept in the database and the
     * patient is allowed to continue filling them out. After the specified number of days, the draft responses would
     * be deleted and the patient would have to start over. If a value is not specified for this setting or if it is
     * {@code -1}, the draft will remain in the database until the patient no longer has access to it.
     *
     * @return A number of days
     */
    int getDraftLifetime();
}
