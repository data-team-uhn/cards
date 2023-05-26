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

import java.io.UnsupportedEncodingException;
import java.nio.file.AccessDeniedException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;

import javax.jcr.InvalidItemStateException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConfiguration;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;
import org.apache.jackrabbit.oak.spi.security.user.util.PasswordUtil;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;

/**
 * Implementation of the {@link TokenManager} service using {@code cards:Token} nodes.
 *
 * @version $Id$
 */
@Component
public class TokenManagerImpl implements TokenManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManagerImpl.class);

    private static final int SECRET_KEY_SIZE = 24;

    private final SecureRandom random = new SecureRandom();

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private TokenConfiguration configuration;

    @Override
    public TokenInfo create(final String userId, final Calendar expiration, final Map<String, String> extraData)
    {
        // Get a service session, since this may be called in a background thread without a user-bound session
        // FIXME This means that every user can create tokens for another user if they manage to call this service;
        // Do we need a tighter check on who's calling the service?
        try (ResourceResolver srr = this.rrf.getServiceResourceResolver(null)) {
            final Session session = srr.adaptTo(Session.class);
            // This is the node where tokens for the target user are stored, /jcr:system/cards:tokens/<username>
            final Node tokensNode = getTokensNode(userId, session);
            if (tokensNode != null) {
                return createTokenNode(tokensNode, expiration, userId, extraData);
            } else {
                LOGGER.warn("Unable to get/create token store for user " + userId);
            }
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public TokenInfo parse(final String loginToken)
    {
        if (loginToken == null) {
            return null;
        }

        // The login token has the format <nodeUUID> or <nodeUUID>-<secretKey>
        // Extract the node UUID
        // The secret key does not need to be used/validated now, so just ignore it
        final String nodeId = StringUtils.substringBefore(loginToken, CardsTokenImpl.TOKEN_DELIMITER);

        try (ResourceResolver srr = this.rrf.getServiceResourceResolver(null)) {
            final Node tokenNode = srr.adaptTo(Session.class).getNodeByIdentifier(nodeId);
            // Check that the node is indeed a valid token node
            if (isValidTokenNode(tokenNode)) {
                // The username is the name of the token node's parent
                String userId = getUser(tokenNode);
                if (userId != null) {
                    // Everything seems correct so far, return a new TokenInfo wrapper around the node
                    return new CardsTokenImpl(tokenNode, loginToken, userId);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.info("Cannot access token {}", loginToken);
        } catch (LoginException e) {
            LOGGER.error("Service access not granted: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Returns the node where tokens for the specified user are stored, {@code /jcr:system/cards:tokens/<username>}. If
     * the node did not already exist when calling this method, a new node will be created.
     *
     * @param user the user for which the token directory is to be retrieved
     * @param session the service session that can be used to read and create nodes
     * @return a JCR node where tokens can be stored, may be {@code null} if the user is {@code null} or manipulating
     *         the repository fails
     */
    private Node getTokensNode(final String userId, final Session session)
    {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            final Node tokensNode = getOrCreateSystemTokensNode(session);
            final Node userTokensNode = getOrCreateUserTokensNode(tokensNode, userId, session);
            return userTokensNode;
        } catch (RepositoryException e) {
            LOGGER.warn("Error while creating tokens node: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Create a new token node below the specified {@code parent}.
     *
     * @param parent the parent node, must be {@code /path/to/someUserProfile/.tokens/}
     * @param expiration the expiration time of the new token
     * @param userId the username of the user that the token will authenticate
     * @param extraData additional attributes of the token to be created, which will also be stored in the session when
     *            the user authenticates using the token
     * @return the new token
     * @throws AccessDeniedException if the editing session cannot access the new token node
     */
    private TokenInfo createTokenNode(final Node parent, final Calendar expiration, final String userId,
        final Map<String, String> extraData)
    {
        try {
            final String tokenName = UUID.randomUUID().toString();
            // Create the node holding the token information
            final Node tokenNode = createParents(parent, tokenName)
                .addNode(tokenName, CardsTokenImpl.TOKEN_NT_NAME);

            // Generate a random secret key that the token holder must present in order to be authenticated
            final String secretKey = generateKey();
            // The identifier of the token node that can be used to retrieve it using session.getNodeByIdentifier
            final String nodeIdentifier = tokenNode.getIdentifier();
            // The actual token that will be passed to the user
            final String loginToken = nodeIdentifier + CardsTokenImpl.TOKEN_DELIMITER + secretKey;
            // The hash stored in the token itself used to validate the authenticity of the token
            try {
                final String keyHash =
                    PasswordUtil.buildPasswordHash(getKeyValue(secretKey, userId), this.configuration.getParameters());
                tokenNode.setProperty(CardsTokenImpl.TOKEN_ATTRIBUTE_KEY, keyHash);
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                LOGGER.warn("Failed to hash token: {}", e.getMessage(), e);
            }
            // Store the expiration date
            tokenNode.setProperty(CardsTokenImpl.TOKEN_ATTRIBUTE_EXPIRY, expiration);
            // Store any other extra data
            if (extraData != null) {
                for (Map.Entry<String, String> data : extraData.entrySet()) {
                    if (!CardsTokenImpl.RESERVED_ATTRIBUTES.contains(data.getKey())) {
                        tokenNode.setProperty(data.getKey(), data.getValue());
                    }
                }
            }
            // Persist the changes
            tokenNode.getSession().save();
            // Return a TokenInfo exposing the new token information
            return new CardsTokenImpl(tokenNode, loginToken, userId);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to create token for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * For improved performance, instead of storing all tokens under the same node, we use a prefix tree (Trie), with
     * nodes for the first 3 groups of 2 hexdigits of the node's name. This method creates the 3 intermediary nodes
     * under the specified user node, and returns the leaf parent. For example, a token for the {@code patient} user
     * named {@code f1049f96-2871-4c14-a65b-90fd624f0df8} will be stored in
     * {@code /jcr:system/cards:tokens/patient/f1/04/9f/f1049f96-2871-4c14-a65b-90fd624f0df8}. This method receives
     * {@code /jcr:system/cards:tokens/patient} as the parent node, creates any of the missing intermediary nodes
     * {@code  f1/04/9f}, and returns {@code 9f}, either as a newly created or an existing node.
     *
     * @param parent the parent node, must be {@code /jcr:system/cards:tokens/<username>}
     * @param name the random UUID name of the token node
     * @return the third intermediary node in the trie under which to store the actual token
     * @throws RepositoryException if accessing or creating the intermediary nodes fails
     */
    private Node createParents(final Node parent, final String name) throws RepositoryException
    {
        Node crt = parent;
        for (int i = 0; i <= 4; i += 2) {
            crt = getOrCreateNode(crt, name.substring(i, i + 2), CardsTokenImpl.TOKENS_NT_NAME, parent.getSession());
        }
        return crt;
    }

    /**
     * Generate a random secret key.
     *
     * @return a secret key consisting only of hexadecimal chars
     */
    private String generateKey()
    {
        byte[] key = new byte[SECRET_KEY_SIZE];
        this.random.nextBytes(key);

        StringBuilder res = new StringBuilder(key.length * 2);
        for (byte b : key) {
            res.append(Text.hexTable[(b >> 4) & 15]);
            res.append(Text.hexTable[b & 15]);
        }
        return res.toString();
    }

    private String getKeyValue(final String key, final String userId)
    {
        return key + userId;
    }

    /**
     * Check that the provided node is a valid token node, i.e. it has the correct nodetype, and is in the correct place
     * for tokens. Other than these basic checks, this does not actually validate the secret key or expiration date of
     * the actual token.
     *
     * @param tokenNode a JCR node
     * @return {@code true} if the node is a non-null valid node for a token, {@code false} otherwise
     */
    private boolean isValidTokenNode(final Node tokenNode)
    {
        if (tokenNode == null || !tokenNode.isNode()) {
            return false;
        }
        try {
            // The expected path is /jcr:system/cards:tokens/<userId>/01/23/45/<tokenNode>
            return tokenNode.getPath().startsWith(CardsTokenImpl.TOKENS_NODE_PATH + "/")
                && tokenNode.isNodeType(CardsTokenImpl.TOKEN_NT_NAME);
        } catch (RepositoryException e) {
            return false;
        }
    }

    /**
     * Extract the username that the token authenticates. The actual validity of the username is not checked.
     *
     * @param tokenNode a valid JCR node storing a token
     * @return a username
     */
    private String getUser(final Node tokenNode)
    {
        try {
            Node crt = tokenNode;
            String name = null;
            // Tokens are stored under /jcr:system/cards:tokens/<username>/<01>/<23>/<45>/<token node>
            // They also used to be stored directly under /jcr:system/cards:tokens/<username>/<token node>
            // To support both kinds of locations, we simply go up until we reach the cards:tokens node
            // and return the name of the node right before that point
            while (!CardsTokenImpl.TOKENS_NODE_PATH.equals(crt.getPath())) {
                name = crt.getName();
                crt = crt.getParent();
            }
            return name;
        } catch (RepositoryException e) {
            LOGGER.warn("Error while determining username for token {}: {}", tokenNode, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Retrieve the grandparent node for tokens, {@code /jcr:system/cards:tokens}.
     *
     * @param session a service session with read/write access to {@code /jcr:system}
     * @return an existing or new node
     * @throws PathNotFoundException if access is not properly granted to read or create the node
     * @throws RepositoryException if accessing the repository fails
     */
    private Node getOrCreateSystemTokensNode(final Session session) throws PathNotFoundException, RepositoryException
    {
        final Node systemNode = session.getRootNode().getNode(CardsTokenImpl.SYSTEM_NODE_NAME);
        return getOrCreateNode(systemNode, CardsTokenImpl.TOKENS_NODE_NAME, CardsTokenImpl.TOKENS_NT_NAME, session);
    }

    /**
     * Retrieve the parent node for a specific user's tokens, {@code /jcr:system/cards:tokens/<username>}.
     *
     * @param tokensNode the grandparent node for storing tokens, {@code /jcr:system/cards:tokens}
     * @param userId the user whose tokens are accessed
     * @param session a service session with read/write access to {@code /jcr:system}
     * @return an existing or new node
     * @throws PathNotFoundException if access is not properly granted to read or create the node
     * @throws RepositoryException if accessing the repository fails
     */
    private Node getOrCreateUserTokensNode(final Node tokensNode, final String userId, final Session session)
        throws PathNotFoundException, RepositoryException
    {
        return getOrCreateNode(tokensNode, userId, CardsTokenImpl.TOKENS_NT_NAME, session);
    }

    /**
     * Retrieve a child node, either already existing or a newly created one.
     *
     * @param parentNode the parent node of the targeted node
     * @param childNodeName a relative node name
     * @param childNodeType in case the target node does not exist yet, the primary node type to use when creating it
     * @param session a session with read/write access to the parent node
     * @return an existing or new node
     * @throws PathNotFoundException if access is not properly granted to read or create the node
     * @throws RepositoryException if accessing the repository fails
     */
    private Node getOrCreateNode(final Node parentNode, final String childNodeName, final String childNodeType,
        final Session session) throws PathNotFoundException, RepositoryException
    {
        Node result;
        try {
            result = parentNode.hasNode(childNodeName)
                ? parentNode.getNode(childNodeName)
                : parentNode.addNode(childNodeName, childNodeType);
            session.save();
        } catch (InvalidItemStateException e) {
            // Perhaps the node was created in a concurrent thread, refresh and try to get it
            session.refresh(false);
            result = parentNode.getNode(childNodeName);
        }
        return result;
    }
}
