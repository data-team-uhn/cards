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
package io.uhndata.cards.auth.token.jwt.impl;

import java.util.Calendar;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.jcr.Node;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
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
@Component(immediate = true, property = "service.ranking:Integer=50", service = { TokenManager.class })
public class CardsJwtTokenManagerImpl implements TokenManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CardsJwtTokenManagerImpl.class);

    private final SecretKey key;

    @Activate
    public CardsJwtTokenManagerImpl(@Reference ResourceResolverFactory rrf)
    {
        SecretKey result = null;
        try (ResourceResolver resolver = rrf.getServiceResourceResolver(null)) {
            String resourcePath = "/jcr:system/cards:jwt/JWTSigningKey";
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode == null) {
                LOGGER.error("Failed to load JWT Signing key: node {} could not be read", resourcePath);
                throw new ExceptionInInitializerError(resourcePath);
            }
            if (keyNode.hasProperty("key")) {
                result = Keys.hmacShaKeyFor(Decoders.BASE64.decode(keyNode.getProperty("key").getString()));
            } else {
                result = Jwts.SIG.HS512.key().build();
                String secretString = Encoders.BASE64.encode(result.getEncoded());
                keyNode.setProperty("key", secretString);
                resolver.commit();
            }
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to load JWT Signing key from node: {}", e.getMessage(), e);
        }
        this.key = result;
    }

    @Override
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration, final Map<String, String> extraData)
    {
        if (this.key == null) {
            // Should not happen
            return null;
        }
        String jws = Jwts.builder()
            .subject(userId)
            .expiration(expiration.getTime())
            .claims(extraData)
            .signWith(this.key)
            .compact();
        return new CardsJwtTokenImpl(jws, userId, expiration, extraData);
    }

    @Override
    public CardsJwtTokenImpl parse(final String loginToken)
    {
        if (loginToken == null || this.key == null) {
            return null;
        }
        try {
            Jwt<?, ?> jwt = Jwts.parser().verifyWith(this.key).build().parseSignedClaims(loginToken);
            return new CardsJwtTokenImpl(jwt, loginToken);
        } catch (JwtException e) {
            // Not a JWT token
        }
        return null;
    }
}
