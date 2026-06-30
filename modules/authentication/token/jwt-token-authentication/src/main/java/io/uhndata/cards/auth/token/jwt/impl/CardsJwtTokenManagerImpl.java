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
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JweHeader;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ClaimsMutator.AudienceCollection;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
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

    private final PrivateKey signingKey;

    private final PublicKey verificationKey;

    private ResourceResolverFactory rrf;

    @Activate
    public CardsJwtTokenManagerImpl(@Reference ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
        PrivateKey result = null;
        PublicKey verification = null;
        try (ResourceResolver resolver = rrf.getServiceResourceResolver(null)) {
            String resourcePath = "/jcr:system/cards:jwt/JWTSigningKey";
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode == null) {
                LOGGER.error("Failed to load JWT Signing key: node {} could not be read", resourcePath);
                throw new ExceptionInInitializerError(resourcePath);
            }
            if (keyNode.hasProperty("key") && keyNode.hasProperty("verify")) {
                // Private key is PKCS-encoded
                byte[] privBytes = Decoders.BASE64.decode(keyNode.getProperty("key").getString());
                result = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));

                // Public key is X.509-encoded
                byte[] pubBytes = Decoders.BASE64.decode(keyNode.getProperty("verify").getString());
                verification = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
            } else {
                KeyPair newPair = Jwts.SIG.RS256.keyPair().build();
                String secretString = Encoders.BASE64.encode(newPair.getPrivate().getEncoded());
                String signingString = Encoders.BASE64.encode(newPair.getPublic().getEncoded());
                keyNode.setProperty("key", secretString);
                keyNode.setProperty("verify", signingString);
                resolver.commit();

                result = newPair.getPrivate();
                verification = newPair.getPublic();
            }
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to load JWT Signing key from node: {}", e.getMessage(), e);
        }
        this.signingKey = result;
        this.verificationKey = verification;
    }

    @Override
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration, final Map<String, String> extraData)
    {
        // Assume our own audience if none is given
        HashSet<String> audience = new HashSet<String>();
        audience.add(CardsJwtTokenImpl.SELF_ID);
        return create(userId, expiration, extraData, audience);
    }

    // Override of the above create that allows for multiple audiences to be set
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration, final Map<String, String> extraData,
            Set<String> audiences)
    {
        if (this.signingKey == null || this.verificationKey == null) {
            // Should not happen
            return null;
        }
        AudienceCollection<JwtBuilder> audBuilder = Jwts.builder()
                .issuer(CardsJwtTokenImpl.SELF_ID)
                .audience();

        for (String aud : audiences) {
            audBuilder.add(aud);
        }

        String jws = audBuilder.and()
            .subject(userId)
            .expiration(expiration.getTime())
            .claims(extraData)
            .header().keyId(CardsJwtTokenImpl.SELF_ID).and()
            .signWith(this.signingKey)
            .compact();
        return new CardsJwtTokenImpl(jws, userId, expiration, extraData);
    }

    private PublicKey lookupPeerKey(final String keyID)
    {
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            String resourcePath = "/jcr:system/cards:jwt/" + keyID;
            Resource res = resolver.resolve(resourcePath);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode == null) {
                LOGGER.error("Failed to load JWT Verification key for peer {}: node {} could not be read", keyID, resourcePath);
                throw new ExceptionInInitializerError(resourcePath);
            }
            if (!keyNode.hasProperty("verify")) {
                LOGGER.error("Failed to load JWT Verification key for peer {}: node {} has no property 'verify'", keyID, resourcePath);
                throw new ExceptionInInitializerError(resourcePath);
            }
            byte[] pubBytes = Decoders.BASE64.decode(keyNode.getProperty("verify").getString());
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to load JWT validation key from node: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public CardsJwtTokenImpl parse(final String loginToken)
    {
        if (loginToken == null || this.verificationKey == null) {
            return null;
        }
        try {
            PublicKey ourVerificationKey = this.verificationKey;
            Jwt<?, ?> jwt = Jwts.parser().keyLocator(new Locator<Key>() {
                @Override
                public Key locate(Header header)
                {
                    String keyID = header instanceof JwsHeader ? ((JwsHeader) header).getKeyId() : ((JweHeader) header).getKeyId();
                    if (keyID == null) {
                        return null;
                    }

                    return keyID.equals(CardsJwtTokenImpl.SELF_ID) ? ourVerificationKey : lookupPeerKey(key_id);
                }
            }).build().parseSignedClaims(loginToken);
            Object payload = jwt.getPayload();
            if (payload instanceof Claims) {
                Claims claims = (Claims) payload;
                // Double-check that we're the intended audience for this JWT
                if (claims.getAudience() == null) {
                    // No audience found, but the token was from a verified source -- assume we're OK
                    return new CardsJwtTokenImpl(jwt, loginToken);
                } else if (claims.getAudience().contains(CardsJwtTokenImpl.SELF_ID)) {
                    // Audience found, and we're in it -- OK
                    return new CardsJwtTokenImpl(jwt, loginToken);
                } else {
                    LOGGER.error("JWT validation failed: Our server ({}) is not in the list of JWT audiences for the given JWT.",
                        CardsJwtTokenImpl.SELF_ID);
                }
            }
        } catch (JwtException e) {
            LOGGER.error("JWT validation failed: {}", e.getMessage());
        }
        return null;
    }
}
