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
import java.util.Map;

import javax.crypto.SecretKey;
import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConfiguration;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.uhndata.cards.auth.token.TokenManager;

/**
 * Implementation of the {@link TokenManager} service using JWT tokens.
 *
 * @version $Id$
 */
@Component(immediate = true, property = "service.ranking:Integer=50")
public class CardsJwtTokenManagerImpl implements TokenManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CardsJwtTokenManagerImpl.class);

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private TokenConfiguration configuration;

    public CardsJwtTokenImpl create(final String userId, final Calendar expiration, final Map<String, String> extraData)
    {
        // Get a service session, since this may be called in a background thread without a user-bound session
        // FIXME This means that every user can create tokens for another user if they manage to call this service;
        // Do we need a tighter check on who's calling the service?
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            SecretKey key = getOrCreateSigningKey(resolver);

            String jws = Jwts.builder()
                .subject(userId)
                .expiration(expiration.getTime())
                .claims(extraData)
                .signWith(key)
                .compact();
            return new CardsJwtTokenImpl(jws, userId, expiration, extraData);
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        }
        return null;
    }

    public CardsJwtTokenImpl parse(final String loginToken)
    {
        if (loginToken == null) {
            return null;
        }

        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            SecretKey key = getOrCreateSigningKey(resolver);
            Jwt<?, ?> jwt = Jwts.parser().verifyWith(key).build().parseSignedClaims(loginToken);
            return new CardsJwtTokenImpl(jwt, loginToken);
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        } catch (JwtException e) {
            // Not a JWT token
            return null;
        }
        return null;
    }

    private SecretKey getOrCreateSigningKey(ResourceResolver resolver)
    {
        String resourcePath = "/libs/cards/conf/JWTSigningKey";
        SecretKey key = null;
        try {
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode.hasProperty("key")) {
                key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(keyNode.getProperty("key").getString()));
            } else {
                key = Jwts.SIG.HS512.key().build();
                String secretString = Encoders.BASE64.encode(key.getEncoded());
                keyNode.setProperty("key", secretString);
                resolver.commit();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load JWT Signing key from node: {}", e.getMessage(), e);
        }
        return key;
    }
}
