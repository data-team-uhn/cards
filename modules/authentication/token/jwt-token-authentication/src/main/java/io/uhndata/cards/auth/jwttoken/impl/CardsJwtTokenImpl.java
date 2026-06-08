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
package io.uhndata.cards.auth.jwttoken.impl;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.api.security.authentication.token.TokenCredentials;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.uhndata.cards.auth.token.CardsToken;

/**
 * Custom implementation of {@link TokenInfo} which works both with the Oak API Tree, used during the authentication
 * process, and the JCR API Node, used post-authentication.
 *
 * @version $Id$
 */
public class CardsJwtTokenImpl implements CardsToken
{
    /** The name of the parent node where tokens for a user are stored. */
    public static final String SYSTEM_NODE_NAME = "jcr:system";

    /** The login token string. */
    private final String loginToken;

    /** The username that the token authenticates. */
    private final String userId;

    /** The expiration time of the token. */
    private final Calendar expirationTime;

    /**
     * Public attributes stored in the token, that, once successfully authenticated, will also be exposed as session
     * attributes.
     */
    private final Map<String, String> attributes;

    /**
     * Parse the attributes forming a token from an existing JWT and the login token containing said JWT.
     * This should generally be used when parsing an inbound JWT from a user.
     * @param jwt The JWT containing the attributes about the current token
     * @param token The login token string said JWT was parsed from
     */
    public CardsJwtTokenImpl(final Jwt<?, ?> jwt, final String token)
    {
        Object payload = jwt.getPayload();
        if (payload instanceof Claims) {
            Claims claims = (Claims) payload;
            this.loginToken = token;
            this.userId = claims.getSubject();
            Calendar expiration = Calendar.getInstance();
            expiration.setTime(claims.getExpiration());
            this.expirationTime = expiration;
            Map<String, String> parsedAttributes = new HashMap<>();
            if (claims.containsKey("cards:sessionSubject")) {
                parsedAttributes.put("cards:sessionSubject", String.valueOf(claims.get("cards:sessionSubject")));
            }
            this.attributes = parsedAttributes;
        } else {
            throw new JwtException("Unhandled payload type");
        }
    }

    /**
     * Create a token from the raw attributes desired.
     * This should generally be used when creating a new token from scratch.
     * @param jws the signed token string that can be provided to users to authenticate with later
     * @param userId the userId of encoded within this token
     * @param expiration the date and time when this token should stop being valid
     * @param attributes any other attributes that are encoded within this token
     */
    public CardsJwtTokenImpl(final String jws, final String userId, final Calendar expiration,
        final Map<String, String> attributes)
    {
        this.loginToken = jws;
        this.userId = userId;
        this.expirationTime = expiration;
        this.attributes = attributes;
    }

    // ------------------------------------------------------< TokenInfo >---

    @Override
    public String getUserId()
    {
        return this.userId;
    }

    @Override
    public String getToken()
    {
        return this.loginToken;
    }

    @Override
    public boolean isExpired(long loginTime)
    {
        return this.expirationTime.toInstant().getEpochSecond() * 1000 < loginTime;
    }

    @Override
    public boolean resetExpiration(long loginTime)
    {
        // CARDS tokens have a fixed expiration time, they don't reset
        return false;
    }

    @Override
    public boolean remove()
    {
        // JWT cannot be invalidated
        return false;
    }

    @Override
    public boolean matches(TokenCredentials tokenCredentials)
    {
        return this.loginToken.equals(tokenCredentials.getToken());
    }

    @Override
    public Map<String, String> getPrivateAttributes()
    {
        // There are no private attributes supported/needed yet
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getPublicAttributes()
    {
        return this.attributes;
    }

    @Override
    public Calendar getExpirationTime()
    {
        return this.expirationTime;
    }
}
