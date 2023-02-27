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
package io.uhndata.cards.auth.token;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;

/**
 * Custom extension of {@link TokenInfo} with more support for extended attributes.
 *
 * @version $Id$
 */
public interface CardsToken extends TokenInfo
{
    /** The name of the parent node where tokens for a user are stored. */
    String TOKENS_NODE_NAME = "cards:tokens";

    /** The name of the parent node where tokens for a user are stored. */
    String TOKENS_NODE_PATH = "/jcr:system/cards:tokens";

    /** The node type for the parent ".tokens" node where tokens for a user are stored. */
    String TOKENS_NT_NAME = "rep:Unstructured";

    /** The node type for a token node. */
    String TOKEN_NT_NAME = "cards:Token";

    /** The name of the JCR attribute where the expiration is stored. */
    String TOKEN_ATTRIBUTE_EXPIRY = "cards:token.exp";

    /** The name of the JCR attribute where the hash of the secret key is stored. */
    String TOKEN_ATTRIBUTE_KEY = "cards:token.key";

    /** Reserved attributes that will not be stored in the session after authentication. */
    List<String> RESERVED_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList(TOKEN_ATTRIBUTE_EXPIRY, TOKEN_ATTRIBUTE_KEY));

    /** Delimiter between the node identifier and the secret key in the token identifier. */
    String TOKEN_DELIMITER = "_";

    /**
     * Obtain the expiration time from this token.
     *
     * @return the expiration date, or {@code null} if there's no expiration date set
     */
    Calendar getExpirationTime();
}
