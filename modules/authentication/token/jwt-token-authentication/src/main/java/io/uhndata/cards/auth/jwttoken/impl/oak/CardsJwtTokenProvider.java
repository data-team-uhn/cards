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
package io.uhndata.cards.auth.jwttoken.impl.oak;

import java.util.Map;

import javax.jcr.Credentials;

import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConstants;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenProvider;

import io.uhndata.cards.auth.token.TokenManager;

/**
 * Custom token provider that uses {@code cards:Token} nodes to store authentication tokens, to be used with the Oak
 * token-based login mechanism.
 *
 * @version $Id$
 */
public class CardsJwtTokenProvider implements TokenProvider, TokenConstants
{
    private final TokenManager tokenManager;
    /**
     * Constructor passing all the needed information.
     *
     * @param tokenManager the token manager that can be used to verify login tokens
     */
    public CardsJwtTokenProvider(final TokenManager tokenManager)
    {
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean doCreateToken(final Credentials credentials)
    {
        // This method is called for automatic creation of the token upon login, which is not how CARDS tokens are
        // managed. Return false to indicate that we don't need to create a new token.
        return false;
    }

    @Override
    public TokenInfo createToken(final Credentials credentials)
    {
        // This method should not be called since doCreateToken already indicated that no token needs to be created
        return null;
    }

    @Override
    public TokenInfo createToken(final String userId, final Map<String, ?> attributes)
    {
        // The recommended way to create a token is through TokenManager.create
        return null;
    }

    @Override
    public TokenInfo getTokenInfo(final String loginToken)
    {
        return this.tokenManager.parse(loginToken);
    }
}
