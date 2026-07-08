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

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

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

    private final SecretKey symmetricKey;

    private final ResourceResolverFactory rrf;

    private final String selfID;

    public CardsJwtVerificationLocatorImpl(PublicKey verificationKey, SecretKey symmetricKey,
        ResourceResolverFactory resolver, String selfID)
    {
        this.verificationKey = verificationKey;
        this.symmetricKey = symmetricKey;
        this.rrf = resolver;
        this.selfID = selfID;
    }

    /**
     * In an instance where the JWT does not belong to us, look up info about them in `/jcr:system/cards:jwt/`.
     *
     * @param keyID The ID under which to find the key
     * @return A node corresponding to the peer's details, or null if we do not have it
     */
    public Node lookupPeerDetails(final String keyID)
    {
        Pattern pattern = Pattern.compile("\\P{Alnum}");
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            // Ensure the keyID is sanitized, disallow usage and return nothing if not
            if (pattern.matcher(keyID).find()) {
                throw new JwtException(String.format("Unsafe peer key: %s", keyID));
            }

            // Grab the appropriate key node, if it exists
            String resourcePath = "/jcr:system/cards:jwt/" + keyID;
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res == null ? null : res.adaptTo(Node.class);
            if (keyNode == null) {
                throw new JwtException(
                    String.format("Failed to load JWT Verification key for peer %s: node %s could not be read",
                        keyID, resourcePath)
                );
            }
            return keyNode;
        } catch (LoginException e) {
            throw new JwtException(String.format("Service access not granted: %s", e.getMessage()));
        }
    }

    private PublicKey getPublicKey(Node keyNode) throws JwtException
    {
        try {
            if (!keyNode.hasProperty(CardsJwtTokenManagerImpl.VERIFY_PROP)) {
                throw new JwtException(
                    String.format("Failed to load JWT Verification key: node %s missing %s property",
                        keyNode.getIdentifier(), CardsJwtTokenManagerImpl.VERIFY_PROP)
                );
            }

            byte[] pubBytes = Decoders.BASE64.decode(
                keyNode.getProperty(CardsJwtTokenManagerImpl.VERIFY_PROP).getString()
            );
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (GeneralSecurityException e) {
            // Should not happen, this is to catch java.security.NoSuchAlgorithmException
            // or java.security.spec.InvalidKeySpecException, but KeyFactory should be able to load RSA
            throw new JwtException(String.format("Failed to load decryption algorithm: %s", e.getMessage()));
        } catch (RepositoryException e) {
            throw new JwtException(String.format("Failed to load JWT Verification key: %s", e.getMessage()));
        }
    }

    /**
     * Extracts the `kid` header from a given JWT Header.
     *
     * @param header The JWT Header
     * @return the `kid` header, or {@code null} if the given header does not correspond to a JwsHeader/JweHeader
     */
    public static String getKey(Header header)
    {
        if (header instanceof JwsHeader) {
            return ((JwsHeader) header).getKeyId();
        } else if (header instanceof JweHeader) {
            return ((JweHeader) header).getKeyId();
        }
        return null;
    }

    @Override
    public Key locate(Header header)
    {
        final String keyID = getKey(header);

        if (keyID == null) {
            // This might instead be a symmetric key -- use the symmetric key
            return this.symmetricKey;
        }

        return keyID.equals(this.selfID) ? this.verificationKey : getPublicKey(lookupPeerDetails(keyID));
    }
}
