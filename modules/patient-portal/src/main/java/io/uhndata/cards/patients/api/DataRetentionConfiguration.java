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

/**
 * Configuration for how the patient data is cleaned up.
 *
 * @version $Id$
 */
public interface DataRetentionConfiguration
{
    /**
     * Check if patient identifying information (name, email) should be deleted as soon as it is no longer needed for
     * emails.
     *
     * @return {@code true} if PII should be deleted
     */
    boolean deleteUnneededPatientDetails();

    /**
     * Check if unsubmitted draft answers should be deleted.
     *
     * @return {@code true} if draft answers should be deleted
     */
    boolean deleteDraftAnswers();

    /**
     * Get the configured amount of time, in days, that patient's draft responses are kept in the database and the
     * patient is allowed to continue filling them out. After the specified number of days, the draft responses would be
     * deleted and the patient would have to start over. If a value is not specified for this setting or if it is
     * {@code -1}, the draft will remain in the database until the patient no longer has access to it. {@code 0} means
     * draft answers will be deleted at every midnight.
     *
     * @return a number of days
     */
    int getDraftLifetime();
}
