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
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.codec.digest.DigestUtils;
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
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtBuilder;
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
    /** JCR property containing our signing key. */
    public static final String SIGNING_KEY_PROP = "key";

    /** JCR property containing a verification key. */
    public static final String VERIFY_PROP = "verify";

    /** JCR property containing a peer's expected 'issuer' ID. */
    public static final String ISSUER_PROP = "iss";

    /** JCR path to the node where we keep our signing/verification keys. */
    public static final String KEY_PATH = "/jcr:system/cards:jwt/JWTRSA256Key";

    private static final Logger LOGGER = LoggerFactory.getLogger(CardsJwtTokenManagerImpl.class);

    private final SecretKey symmetricKey;

    private final PrivateKey signingKey;

    private final PublicKey verificationKey;

    private final String selfAud;

    private final String selfID;

    private ResourceResolverFactory rrf;

    @Activate
    public CardsJwtTokenManagerImpl(@Reference ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
        this.selfAud = CardsJwtTokenImpl.SELF_ID.replaceAll("\\P{Alnum}", "");
        PrivateKey result = null;
        PublicKey verification = null;
        SecretKey symmetric = null;
        try (ResourceResolver resolver = rrf.getServiceResourceResolver(null)) {
            Resource res = resolver.resolve(CardsJwtTokenManagerImpl.KEY_PATH);
            Node keyNode = res.adaptTo(Node.class);
            if (keyNode == null) {
                LOGGER.error("Failed to load JWT Signing key: node {} could not be read",
                    CardsJwtTokenManagerImpl.KEY_PATH);
                throw new ExceptionInInitializerError(CardsJwtTokenManagerImpl.KEY_PATH);
            }

            if (keyNode.hasProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP)
                && keyNode.hasProperty(CardsJwtTokenManagerImpl.VERIFY_PROP)) {
                // Private key is PKCS-encoded
                result = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(readKey(keyNode, CardsJwtTokenManagerImpl.SIGNING_KEY_PROP)));
                // Public key is X.509-encoded
                verification = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(readKey(keyNode, CardsJwtTokenManagerImpl.VERIFY_PROP)));
            } else {
                KeyPair newPair = Jwts.SIG.RS256.keyPair().build();
                keyNode.setProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP,
                    Encoders.BASE64.encode(newPair.getPrivate().getEncoded()));
                keyNode.setProperty(CardsJwtTokenManagerImpl.VERIFY_PROP,
                    Encoders.BASE64.encode(newPair.getPublic().getEncoded()));
                resolver.commit();

                result = newPair.getPrivate();
                verification = newPair.getPublic();
            }

            // For backwards compatibility: attempt to load (but not generate) a symmetric key, if it exists
            res = resolver.resolve("/jcr:system/cards:jwt/JWTSigningKey");
            keyNode = res == null ? null : res.adaptTo(Node.class);
            if (keyNode != null && keyNode.hasProperty(CardsJwtTokenManagerImpl.SIGNING_KEY_PROP)) {
                symmetric = Keys.hmacShaKeyFor(Decoders.BASE64.decode(keyNode.getProperty(
                    CardsJwtTokenManagerImpl.SIGNING_KEY_PROP).getString()));
            }
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to load JWT Signing key from node: {}", e.getMessage(), e);
        }
        this.signingKey = result;
        this.verificationKey = verification;
        this.symmetricKey = symmetric;
        this.selfID = getFingerprint(verification);
    }

    /**
     * Read a BASE64-encoded key from the given property of the given node.
     *
     * @param keyNode The node to extract a key from
     * @param propName The property containing the key
     * @return A byte array from the BASE64-encoded key
     */
    private byte[] readKey(Node keyNode, String propName) throws RepositoryException
    {
        return Decoders.BASE64.decode(
            keyNode.getProperty(propName).getString()
        );
    }

    @Override
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration,
        final Map<String, String> extraData)
    {
        // Assume our own audience if none is given
        return create(userId, expiration, extraData, Set.of(this.selfAud));
    }

    /**
     * Obtain a fingerprint from the given public key.
     *
     * @param publicKey The key to obtain a fingerprint for
     * @return A string hash that should uniquely identify the given public key
     */
    public static String getFingerprint(final PublicKey publicKey)
    {
        // Obtain a fingerprint from a key
        if (publicKey == null)
        {
            return null;
        }

        try
        {
            return DigestUtils.sha256Hex(Encoders.BASE64.encode(publicKey.getEncoded()));
        } catch (Exception e) {
            // TODO: Better exception handling
            return null;
        }
    }

    /**
     * Create a JWT with the given specifications.
     *
     * @param userId The user ID to assign as a subject
     * @param expiration An expiration time for the JWT
     * @param extraData Additional data to include in the JWT payload
     * @param audiences A set of audiences that the JWT is meant for
     * @return A CardsJwtTokenImpl corresponding to a newly minted JWT with the given parameters.
     */
    public CardsJwtTokenImpl create(final String userId, final Calendar expiration,
        final Map<String, String> extraData, Set<String> audiences)
    {
        if (this.signingKey == null || this.verificationKey == null) {
            // Should not happen
            return null;
        }
        AudienceCollection<JwtBuilder> audBuilder = Jwts.builder()
                .issuer(this.selfAud)
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

    private boolean areClaimsValid(Jwt<?, ?> jwt, CardsJwtVerificationLocatorImpl locator) throws JwtException,
        RepositoryException
    {
        // Double check claims in the payload:
        Object payload = jwt.getPayload();
        if (payload instanceof Claims) {
            Claims claims = (Claims) payload;
            String keyID = CardsJwtVerificationLocatorImpl.getKey(jwt.getHeader());
            if (keyID == null) {
                // We were signed using a symmetric key (no `kid` present): we accept for backwards compatability
                return true;
            }

            String issuer = keyID.equals(this.selfID) ? this.selfAud : locator.lookupPeerDetails(keyID)
                .getProperty(CardsJwtTokenManagerImpl.ISSUER_PROP).getString();

            // If we're signed using an asymmetric key, double-check that we're the intended audience for this JWT
            if (claims.getAudience() == null) {
                // No audience found, reject
                throw new JwtException("The given JWT is missing an `aud` claim.");
            } else if (!claims.getAudience().contains(this.selfAud)) {
                throw new JwtException("Our server (" + this.selfAud
                    + ")) is not in the list of JWT audiences for the given JWT.");
            } else if (!claims.getIssuer().equals(issuer)) {
                throw new JwtException("The given JWT's issuer does not match the expected issuer.");
            }
            return true;
        } else {
            throw new JwtException("The given JWT's payload is in an unknown format.");
        }
    }

    @Override
    public CardsJwtTokenImpl parse(final String loginToken)
    {
        if (loginToken == null || this.verificationKey == null) {
            return null;
        }
        try {
            CardsJwtVerificationLocatorImpl locator = new CardsJwtVerificationLocatorImpl(this.verificationKey,
                this.symmetricKey, this.rrf, this.selfID);

            Jwt<?, ?> jwt = Jwts.parser()
                .keyLocator(locator)
                .build()
                .parseSignedClaims(loginToken);

            // Double check claims in the payload:
            if (areClaimsValid(jwt, locator)) {
                return new CardsJwtTokenImpl(jwt, loginToken);
            }
        } catch (JwtException e) {
            LOGGER.error("JWT validation failed: {}", e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.error("JWT validation failed: {}", e.getMessage());
        }
        return null;
    }
}
