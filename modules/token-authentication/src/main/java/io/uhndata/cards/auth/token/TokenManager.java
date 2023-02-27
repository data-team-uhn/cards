/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.uhndata.cards.auth.token;

import java.util.Calendar;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Service that can create and read tokens.
 *
 * @version $Id$
 */
@ProviderType
public interface TokenManager
{
    /**
     * Create and persist a new token.
     *
     * @param user local username to associate with the token
     * @param expiration date after which the token becomes invalid
     * @param extraData optional data to store in the token
     * @return a new token
     */
    CardsToken create(String user, Calendar expiration, Map<String, String> extraData);

    /**
     * Parse a token's data from its identifier.
     *
     * @param token a token identifier, either in the format {@code node-uuid_secret-key}, or just {@code node-uuid}
     * @return the parsed token data, or {@code null} if the input is invalid
     */
    CardsToken parse(String token);
}
