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
package io.uhndata.cards.auth.token.impl;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.authentication.token.TokenCredentials;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.namespace.NamespaceConstants;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;
import org.apache.jackrabbit.oak.spi.security.user.util.PasswordUtil;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.CardsToken;

/**
 * Custom implementation of {@link TokenInfo} which works both with the Oak API Tree, used during the authentication
 * process, and the JCR API Node, used post-authentication.
 *
 * @version $Id$
 */
public class CardsTokenImpl implements CardsToken
{
    /** The name of the parent node where tokens for a user are stored. */
    public static final String SYSTEM_NODE_NAME = "jcr:system";

    private static final Logger LOGGER = LoggerFactory.getLogger(CardsTokenImpl.class);

    /**
     * The JCR node where the token is stored. Either this or {@code tokenTree} must be set, depending on which API was
     * used to create the object.
     */
    private final Node tokenNode;

    /**
     * The Oak tree where the token is stored. Either this or {@code tokenNode} must be set, depending on which API was
     * used to create the object.
     */
    private final Tree tokenTree;

    /** Oak root, which can be used to remove tokens when trying to authenticate using an expired token. */
    private final Root root;

    /** The login token string. */
    private final String loginToken;

    /** The username that the token authenticates. */
    private final String userId;

    /** The expiration time of the token. */
    private final Calendar expirationTime;

    /** The hashed secret, part of the public token. */
    private final String validationKey;

    /**
     * Public attributes stored in the token, that, once successfully authenticated, will also be exposed as session
     * attributes.
     */
    private final Map<String, String> attributes;

    /**
     * Constructor to be used once a JCR session is available, after authentication, to expose the token details to code
     * using the {@code TokenManager} service.
     *
     * @param tokenNode a JCR node storing a token
     * @param token the login token
     * @param userId the user that the token authenticates
     */
    public CardsTokenImpl(final Node tokenNode, final String token, final String userId)
    {
        this.tokenNode = tokenNode;
        this.root = null;
        this.tokenTree = null;
        this.loginToken = token;
        this.userId = userId;
        this.expirationTime = parseExpirationTime();
        this.validationKey = getValidationKey();

        final Map<String, String> storedAttributes = new HashMap<>();
        try {
            for (final PropertyIterator it = tokenNode.getProperties(); it.hasNext();) {
                final Property p = it.nextProperty();
                final String name = p.getName();
                final String value = p.getString();
                if (RESERVED_ATTRIBUTES.contains(name)) {
                    continue;
                } else if (!isSystemProperty(name)) {
                    // This is not a reserved or system property, thus it is a simple stored attribute
                    storedAttributes.put(name, value);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access token: {}", e.getMessage(), e);
        }
        this.attributes = Collections.unmodifiableMap(storedAttributes);
    }

    /**
     * Constructor to be during authentication, to load and validate the token through the {@code TokenLoginModule}.
     *
     * @param root the Oak session root, exposing the repository
     * @param tokenTree an Oak tree node storing a token
     * @param token the login token
     * @param userId the user that the token authenticates
     */
    public CardsTokenImpl(final Root root, final Tree tokenTree, final String token, final String userId)
    {
        this.tokenNode = null;
        this.root = root;
        this.tokenTree = tokenTree;
        this.loginToken = token;
        this.userId = userId;
        this.expirationTime = parseExpirationTime();
        this.validationKey = getValidationKey();

        Map<String, String> storedAttributes = new HashMap<>();
        for (final PropertyState p : this.tokenTree.getProperties()) {
            final String name = p.getName();
            final String value = p.getValue(Type.STRING);
            if (RESERVED_ATTRIBUTES.contains(name)) {
                continue;
            } else if (!isSystemProperty(name)) {
                // This is not a reserved or system property, thus it is a simple stored attribute
                storedAttributes.put(name, value);
            }
        }
        this.attributes = Collections.unmodifiableMap(storedAttributes);
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
        try {
            if (this.tokenNode != null) {
                // This will not work, tokens are loaded by the token manager in a service session closed as soon as the
                // token is loaded; removing tokens should be done directly by removing the node itself.
                this.tokenNode.remove();
                this.tokenNode.getSession().save();
            } else if (this.tokenTree != null) {
                this.tokenTree.remove();
                this.root.commit();
            }
            return true;
        } catch (RepositoryException | CommitFailedException e) {
            LOGGER.debug("Error while removing token {}: {}", this.loginToken, e.getMessage());
        }
        return false;
    }

    @Override
    public boolean matches(TokenCredentials tokenCredentials)
    {
        // This requires checking that the hash, stored in the token node, matches the secret key, presented in the
        // login token

        // The login token contains both the token node UUID, and the secret key, so extract just the key
        final String credentialsToken = StringUtils.substringAfter(tokenCredentials.getToken(), TOKEN_DELIMITER);

        // Check the validity of the login token
        if (this.validationKey == null
            || !PasswordUtil.isSame(this.validationKey, computeSecretToken(credentialsToken, this.userId))) {
            return false;
        }

        // All good, update credential attributes with the data in the token.
        // The attributes will then be added to the session, which makes them usable post-authentication in other code.
        Collection<String> attrNames = Arrays.asList(tokenCredentials.getAttributeNames());
        for (Map.Entry<String, String> attr : this.attributes.entrySet()) {
            String name = attr.getKey();
            if (!attrNames.contains(name)) {
                tokenCredentials.setAttribute(name, attr.getValue());
            }
        }
        return true;
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

    /**
     * Returns {@code true} if the specified property name has a 'jcr', 'rep', or other system namespace prefix;
     * {@code false} otherwise.
     *
     * @param propertyName the property name to test
     * @return {@code true} if the specified property name seems to represent repository internal information
     */
    private boolean isSystemProperty(String propertyName)
    {
        String prefix = Text.getNamespacePrefix(propertyName);
        return NamespaceConstants.RESERVED_PREFIXES.contains(prefix);
    }

    /**
     * Extracts the expiration date from the token node.
     *
     * @return the expiration date, or {@code null} if there's no expiration date set or accessing it fails
     */
    private Calendar parseExpirationTime()
    {
        if (this.tokenNode != null) {
            try {
                if (this.tokenNode.hasProperty(TOKEN_ATTRIBUTE_EXPIRY)) {
                    return this.tokenNode.getProperty(TOKEN_ATTRIBUTE_EXPIRY).getDate();
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to access token expiration date for {}: {}", this.loginToken, e.getMessage(), e);
            }
        } else if (this.tokenTree != null && this.tokenTree.hasProperty(TOKEN_ATTRIBUTE_EXPIRY)) {
            return ISO8601.parse(this.tokenTree.getProperty(TOKEN_ATTRIBUTE_EXPIRY).getValue(Type.DATE));
        }
        return null;
    }

    @Override
    public Calendar getExpirationTime()
    {
        return this.expirationTime;
    }

    /**
     * Extracts the key hash from the token node.
     *
     * @return the validation hash, or {@code null} if there's no hash set or accessing it fails
     */
    private String getValidationKey()
    {
        if (this.tokenNode != null) {
            try {
                if (this.tokenNode.hasProperty(TOKEN_ATTRIBUTE_KEY)) {
                    return this.tokenNode.getProperty(TOKEN_ATTRIBUTE_KEY).getString();
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to access token validation key for {}: {}", this.loginToken, e.getMessage(), e);
            }
        } else if (this.tokenTree != null && this.tokenTree.hasProperty(TOKEN_ATTRIBUTE_KEY)) {
            return this.tokenTree.getProperty(TOKEN_ATTRIBUTE_KEY).getValue(Type.STRING);
        }
        return null;
    }

    /**
     * Compute the secret token from its constituent parts.
     *
     * @param key the secret key that validates the token authenticity
     * @param userId the username for which the token is valid
     * @return the secret token
     */
    private String computeSecretToken(final String key, final String userId)
    {
        return key + userId;
    }
}
