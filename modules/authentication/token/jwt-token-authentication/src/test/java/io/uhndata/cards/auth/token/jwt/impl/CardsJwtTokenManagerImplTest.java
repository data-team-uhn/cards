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

import java.security.KeyPair;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CardsJwtTokenManagerImpl}, the JWT-backed
 * {@code TokenManager}.
 *
 * <p>
 * These pin the current behaviour of self-issued, symmetric (HMAC) tokens so
 * that future changes to
 * {@code parse()} -- such as adding support for tokens issued by trusted
 * external providers -- can be made without
 * regressing the existing patient-portal flow, where a token minted by
 * {@code create()} must round-trip back through
 * {@code parse()} and yield the same user and session subject.
 * </p>
 *
 * <p>
 * On activation the manager reads its HMAC signing key from
 * {@code /jcr:system/cards:jwt/JWTSigningKey}; here that
 * lookup is mocked to return a freshly generated, valid HS512 key, so no
 * repository is needed.
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CardsJwtTokenManagerImplTest
{
    /**
     * The public claim used by the patient portal to carry the visit/subject path.
     */
    private static final String SESSION_SUBJECT = "cards:sessionSubject";

    private static final String KEY_PATH = "/jcr:system/cards:jwt/JWTRSA256Key";

    private static final String SELF_ID = "localhost8080";

    private static final String PEER_ID = "localhost8081";

    private static final String PEER_KEY_PATH_PREFIX = "/jcr:system/cards:jwt/";

    @Mock
    private ResourceResolverFactory resolverFactory;

    @Mock
    private ResourceResolver resolver;

    @Mock
    private Resource keyResource;

    @Mock
    private Node keyNode;

    @Mock
    private Property keyProperty;

    @Mock
    private Property verifyProperty;

    @Mock
    private Property issuerProperty;

    @Mock
    private Resource peerResource;

    @Mock
    private Node peerNode;

    @Mock
    private Property peerVerifyProperty;

    @Mock
    private Property peerIssuerProperty;

    private CardsJwtTokenManagerImpl manager;

    private KeyPair peerPair;

    private String peerFingerprint;

    @Before
    public void setUp() throws Exception
    {
        // Generate a RS256 keypair and expose it exactly as the component reads it from
        // the repository.
        final KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
        when(this.resolverFactory.getServiceResourceResolver(any())).thenReturn(this.resolver);
        when(this.resolver.resolve(KEY_PATH)).thenReturn(this.keyResource);
        when(this.keyResource.adaptTo(Node.class)).thenReturn(this.keyNode);
        when(this.keyNode.hasProperty("key")).thenReturn(true);
        when(this.keyNode.getProperty("key")).thenReturn(this.keyProperty);
        when(this.keyNode.hasProperty("verify")).thenReturn(true);
        when(this.keyNode.getProperty("verify")).thenReturn(this.verifyProperty);
        when(this.keyProperty.getString()).thenReturn(Encoders.BASE64.encode(keyPair.getPrivate().getEncoded()));
        when(this.verifyProperty.getString()).thenReturn(Encoders.BASE64.encode(keyPair.getPublic().getEncoded()));

        this.peerPair = Jwts.SIG.RS256.keyPair().build();
        this.peerFingerprint = CardsJwtTokenManagerImpl.getFingerprint(this.peerPair.getPublic());
        when(this.resolver.resolve(PEER_KEY_PATH_PREFIX + this.peerFingerprint)).thenReturn(this.peerResource);
        when(this.peerResource.adaptTo(Node.class)).thenReturn(this.peerNode);
        when(this.peerNode.hasProperty("verify")).thenReturn(true);
        when(this.peerNode.getProperty("verify")).thenReturn(this.peerVerifyProperty);
        when(this.peerNode.getProperty("iss")).thenReturn(this.peerIssuerProperty);
        when(this.peerVerifyProperty.getString()).thenReturn(
            Encoders.BASE64.encode(this.peerPair.getPublic().getEncoded()));
        when(this.peerIssuerProperty.getString()).thenReturn(PEER_ID);

        // Activate the component via its @Activate constructor.
        this.manager = new CardsJwtTokenManagerImpl(this.resolverFactory);
    }

    @Test
    public void createThenParseRoundTripsUserIdAndSubject()
    {
        final CardsJwtTokenImpl created = this.manager.create("guest-patient", oneHourFromNow(),
                Map.of(SESSION_SUBJECT, "/Subjects/v1"));
        final String token = created.getToken();

        // A JWT is three base64url segments separated by dots (the same check the auth
        // handler applies).
        Assert.assertTrue("Issued token is not a well-formed JWT",
                token.matches("^[\\w-_]+\\.[\\w-_]+\\.[\\w-_]+$"));

        final CardsJwtTokenImpl parsed = this.manager.parse(token);
        Assert.assertNotNull("A freshly issued token must parse back", parsed);
        Assert.assertEquals("guest-patient", parsed.getUserId());
        Assert.assertEquals("/Subjects/v1", parsed.getPublicAttributes().get(SESSION_SUBJECT));
    }

    @Test
    public void parseRejectsNull()
    {
        Assert.assertNull(this.manager.parse(null));
    }

    @Test
    public void parseRejectsMalformedToken()
    {
        Assert.assertNull(this.manager.parse("garbage"));
        Assert.assertNull(this.manager.parse("this.is.not-a-real-jwt"));
    }

    @Test
    public void parseRejectsExpiredToken()
    {
        final Calendar past = Calendar.getInstance();
        past.add(Calendar.HOUR_OF_DAY, -1);
        final String expired = this.manager.create("guest-patient", past, Map.of(SESSION_SUBJECT, "/Subjects/v1"))
                .getToken();
        Assert.assertNull("An expired token must not parse", this.manager.parse(expired));
    }

    @Test
    public void parseRejectsTokenSignedWithForeignKey()
    {
        // A well-formed token signed with some other key must be rejected: verification
        // uses this instance's own
        // secret. This is exactly the boundary that cross-provider support would later
        // have to open up deliberately.
        final String foreign = Jwts.builder()
                .subject("attacker")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Jwts.SIG.RS256.keyPair().build().getPrivate())
                .compact();
        Assert.assertNull("A token signed with a different key must not parse", this.manager.parse(foreign));
    }

    @Test
    public void createThenParseRejectsInvalidAudience()
    {
        // Create a real token, but exclude ourselves from the audience, and then make sure we fail to parse
        Set<String> fakeAudience = new HashSet<String>();
        fakeAudience.add("not_you");
        final CardsJwtTokenImpl created = this.manager.create("guest-patient", oneHourFromNow(),
                Map.of(SESSION_SUBJECT, "/Subjects/v1"), fakeAudience);
        final String token = created.getToken();

        // A JWT is three base64url segments separated by dots (the same check the auth
        // handler applies).
        Assert.assertTrue("Issued token is not a well-formed JWT",
                token.matches("^[\\w-_]+\\.[\\w-_]+\\.[\\w-_]+$"));
        Assert.assertNull("A token signed with an audience that does not include us must not parse",
                this.manager.parse(token));
    }

    @Test
    public void createForeignKeyThenAccept()
    {
        // Test using a second set of keys that we've accepted
        String selfID = CardsJwtTokenManagerImpl.SELF_ID.replaceAll("\\P{Alnum}", "");

        final String foreign = Jwts.builder()
            .issuer("localhost8081")
            .audience().add(selfID).and()
            .expiration(oneHourFromNow().getTime())
            .header().keyId(this.peerFingerprint).and()
            .signWith(this.peerPair.getPrivate())
            .compact();
        Assert.assertNotNull("A foreign, trusted issued token must parse back", this.manager.parse(foreign));
    }

    private static Calendar oneHourFromNow()
    {
        final Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR_OF_DAY, 1);
        return c;
    }
}
