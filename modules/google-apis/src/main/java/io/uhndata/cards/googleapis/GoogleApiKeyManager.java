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
package io.uhndata.cards.googleapis;

/**
 * Service for Google API key management.
 *
 * @version $Id$
 */
public interface GoogleApiKeyManager
{
    /**
     * Retrieves the Google API key configured in the system.
     *
     * @return the configured key, or the empty string if no key is configured.
     */
    String getAPIKey();

    /**
     * Retrieves Google API key from the OS environment variable. If the environment variable is not specified
     * returns an empty string.
     *
     * @return Google API key
     */
    String getAPIKeyFromEnvironment();

    /**
     * Retrieves Google API key from the node. If the key is not specified returns an empty string.
     *
     * @return Google API key
     */
    String getAPIKeyFromNode();
}
