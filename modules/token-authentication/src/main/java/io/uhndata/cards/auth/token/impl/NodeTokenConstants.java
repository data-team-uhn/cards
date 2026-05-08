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
package io.uhndata.cards.auth.token.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JCR storage constants for node-based tokens describing how these tokens are persisted as JCR nodes.
 *
 * @version $Id$
 */

@SuppressWarnings("checkstyle:InterfaceIsType")
public interface NodeTokenConstants
{
    /** The name of the parent node where tokens for a user are stored. */
    String TOKENS_NODE_NAME = "cards:tokens";

    /** The path of the parent node where tokens for a user are stored. */
    String TOKENS_NODE_PATH = "/jcr:system/cards:tokens";

    /** The node type for the parent node where tokens for a user are stored. */
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
}
