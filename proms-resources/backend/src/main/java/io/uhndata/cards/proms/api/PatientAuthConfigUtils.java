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
package io.uhndata.cards.proms.api;

/**
 * Basic utilities for grabbing configuration details from the patient identification node.
 *
 * @version $Id$
 */
public interface PatientAuthConfigUtils
{
    /** The location of the configuration node for patient auth. */
    String CONFIG_NODE = "/Proms/PatientIdentification";

    /** Property on config node for whether or not tokenless auth is enabled. */
    String TOKENLESS_AUTH_ENABLED_PROP = "enableTokenlessAuth";

    /** Property on config node for whether or not patient identification is required. */
    String PATIENT_IDENTIFICATION_REQUIRED_PROP = "requirePIIAuth";

    /** Property on config node for the number of days a token is valid for. */
    String TOKEN_LIFETIME_PROP = "tokenLifetime";

    /** Whether or not tokenless auth is enabled by default (used in case of errors). */
    Boolean TOKENLESS_AUTH_ENABLED_DEFAULT = false;

    /** Whether or not patient identification is required by default (used in case of errors). */
    Boolean PATIENT_IDENTIFICATION_REQUIRED_DEFAULT = true;

    /** The number of days a token is valid for by default (used in case of errors). */
    int TOKEN_LIFETIME_DEFAULT = 0;

    /**
     * Check if tokenless authentication is enabled.
     *
     * @return True if tokenless authentication is enabled, {@code false} if the configuration could not be found or
     *         it is disabled
     */
    boolean tokenlessAuthEnabled();

    /**
     * Check if patient self-identification is required.
     *
     * @return True if patient identification is required or if the configuration could not be found,
     *         {@code false} otherwise
     */
    boolean patientIdentificationRequired();

    /**
     * Obtain the amount of time after an appointment ends that tokens to questionnaires should be valid for.
     *
     * @return The amount of time after an appointment ends that tokens to questionnaires should be valid for
     */
    int tokenLifetime();
}
