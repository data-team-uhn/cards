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
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsMutator.AudienceCollection;
import io.jsonwebtoken.JweHeader;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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
    /** JCR property containing our signing key. */
    public static final String SIGNING_KEY_PROP = "key";

    /** JCR property containing a verification key. */
    public static final String VERIFY_PROP = "verify";

    private static final Logger LOGGER = LoggerFactory.getLogger(CardsJwtTokenManagerImpl.class);

    private final PrivateKey signingKey;

    private final PublicKey verificationKey;

    private final String selfID;

    private ResourceResolverFactory rrf;

    @Activate
    public CardsJwtTokenManagerImpl(@Reference ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
        this.selfID = CardsJwtTokenImpl.SELF_ID.replaceAll("\\P{Alnum}", "");
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

            if (keyNode.hasProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP)
                && keyNode.hasProperty(CardsJwtTokenManagerImpl.VERIFY_PROP)) {
                // Private key is PKCS-encoded
                byte[] privBytes = Decoders.BASE64.decode(
                    keyNode.getProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP).getString()
                );
                result = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));

                // Public key is X.509-encoded
                byte[] pubBytes = Decoders.BASE64.decode(keyNode.getProperty(
                    CardsJwtTokenManagerImpl.VERIFY_PROP).getString()
                );
                verification = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
            } else {
                KeyPair newPair = Jwts.SIG.RS256.keyPair().build();
                String secretString = Encoders.BASE64.encode(newPair.getPrivate().getEncoded());
                String signingString = Encoders.BASE64.encode(newPair.getPublic().getEncoded());
                keyNode.setProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP, secretString);
                keyNode.setProperty(CardsJwtTokenManagerImpl.VERIFY_PROP, signingString);
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
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration,
        final Map<String, String> extraData)
    {
        // Assume our own audience if none is given
        Set<String> audience = new HashSet<String>();
        audience.add(this.selfID);
        return create(userId, expiration, extraData, audience);
    }

    // Override of the above create that allows for multiple audiences to be set
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration,
        final Map<String, String> extraData, Set<String> audiences)
    {
        if (this.signingKey == null || this.verificationKey == null) {
            // Should not happen
            return null;
        }
        AudienceCollection<JwtBuilder> audBuilder = Jwts.builder()
                .issuer(this.selfID)
                .audience();

        for (String aud : audiences) {
            audBuilder.add(aud);
        }

        String jws = audBuilder.and()
            .subject(userId)
            .expiration(expiration.getTime())
            .claims(extraData)
            .header().keyId(this.selfID).and()
            .signWith(this.signingKey)
            .compact();
        return new CardsJwtTokenImpl(jws, userId, expiration, extraData);
    }

    @Override
    public CardsJwtTokenImpl parse(final String loginToken)
    {
        if (loginToken == null || this.verificationKey == null) {
            return null;
        }
        try {
            PublicKey ourVerificationKey = this.verificationKey;
            Jwt<?, ?> jwt = Jwts.parser()
                .keyLocator(new CardsJwtVerificationLocatorImpl(this.verificationKey, this.rrf, this.selfID))
                .build()
                .parseSignedClaims(loginToken);

            // Double check claims in the payload:
            Object payload = jwt.getPayload();
            if (payload instanceof Claims) {
                Claims claims = (Claims) payload;
                String keyID = jwt.getHeader() instanceof JwsHeader ? ((JwsHeader) jwt.getHeader()).getKeyId()
                    : ((JweHeader) jwt.getHeader()).getKeyId();
                // Double-check that we're the intended audience for this JWT
                if (claims.getAudience() == null) {
                    // No audience found, reject
                    throw new JwtException("The given JWT is missing an `aud` claim.");
                } else if (!claims.getAudience().contains(this.selfID)) {
                    throw new JwtException("Our server (" + this.selfID
                        + ")) is not in the list of JWT audiences for the given JWT.");
                } else if (!claims.getIssuer().equals(keyID)) {
                    throw new JwtException("The given JWT's issuer does not match the KeyID passed in its header.");
                } else {
                    // Audience found, and we're in it -- OK
                    return new CardsJwtTokenImpl(jwt, loginToken);
                }
            }
        } catch (JwtException e) {
            LOGGER.error("JWT validation failed: {}", e.getMessage());
        }
        return null;
    }
}
