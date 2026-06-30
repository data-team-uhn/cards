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

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.jcr.Node;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JweHeader;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.io.Decoders;

/**
 * Implementation of a {@link Locator} for JWT validation key lookup.
 *
 * @version $Id$
 */
public class CardsJwtVerificationLocatorImpl implements Locator<Key>
{
    private final PublicKey verificationKey;

    private final ResourceResolverFactory rrf;

    private final String selfID;

    public CardsJwtVerificationLocatorImpl(PublicKey verificationKey, ResourceResolverFactory resolver, String selfID)
    {
        this.verificationKey = verificationKey;
        this.rrf = resolver;
        this.selfID = selfID;
    }

    /**
     * In an instance where the JWT does not belong to us, look up the peer key in `/jcr:system/cards:jwt/`.
     */
    private PublicKey lookupPeerKey(final String keyID)
    {
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            // Ensure the keyID is sanitized, disallow usage and return nothing if not
            if (keyID.indexOf("\\P{Alnum}") >= 0) {
                throw new JwtException(String.format("Unsafe peer key: {}", keyID));
            }

            // Grab the appropriate key node, if it exists
            String resourcePath = "/jcr:system/cards:jwt/" + keyID;
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode == null) {
                throw new JwtException(
                    String.format("Failed to load JWT Verification key for peer %s: node %s could not be read",
                        keyID, resourcePath)
                );
            }
            if (!keyNode.hasProperty(CardsJwtTokenManagerImpl.VERIFY_PROP)) {
                throw new JwtException(
                    String.format("Failed to load JWT Verification key for peer %s: node %s could not be read",
                        keyID, resourcePath)
                );
            }
            byte[] pubBytes = Decoders.BASE64.decode(
                keyNode.getProperty(CardsJwtTokenManagerImpl.VERIFY_PROP).getString()
            );
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (LoginException e) {
            throw new JwtException(String.format("Service access not granted: {}", e.getMessage()));
        } catch (Exception e) {
            throw new JwtException(String.format("Failed to load JWT validation key from node: {}", e.getMessage()));
        }
    }

    @Override
    public Key locate(Header header)
    {
        final String keyID = header instanceof JwsHeader ? ((JwsHeader) header).getKeyId()
            : ((JweHeader) header).getKeyId();

        if (keyID == null) {
            return null;
        }

        return keyID.equals(this.selfID) ? this.verificationKey : lookupPeerKey(keyID);
    }
}
