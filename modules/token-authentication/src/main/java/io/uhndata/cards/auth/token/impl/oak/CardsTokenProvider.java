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
package io.uhndata.cards.auth.token.impl.oak;

import java.util.Map;

import javax.jcr.Credentials;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.plugins.identifier.IdentifierManager;
import org.apache.jackrabbit.oak.plugins.tree.TreeUtil;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConstants;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenProvider;

import io.uhndata.cards.auth.token.CardsToken;
import io.uhndata.cards.auth.token.impl.CardsTokenImpl;

/**
 * Custom token provider that uses {@code cards:Token} nodes to store authentication tokens, to be used with the Oak
 * token-based login mechanism.
 *
 * @version $Id$
 */
public class CardsTokenProvider implements TokenProvider, TokenConstants
{
    /** Oak API equivalent to a JCR repository+session. */
    private final Root root;

    /** Oak API that can retrieve a Tree given its internal identifier. */
    private final IdentifierManager identifierManager;

    /**
     * Constructor passing all the needed information.
     *
     * @param root the current root that can be used to read and commit trees
     */
    public CardsTokenProvider(final Root root)
    {
        this.root = root;
        this.identifierManager = new IdentifierManager(this.root);
    }

    @Override
    public boolean doCreateToken(final Credentials credentials)
    {
        // This method is called for automatic creation of the token upon login, which is not how CARDS tokens are
        // managed. Return false to indicate that we don't need to create a new token.
        return false;
    }

    @Override
    public TokenInfo createToken(final Credentials credentials)
    {
        // This method should not be called since doCreateToken already indicated that no token needs to be created
        return null;
    }

    @Override
    public TokenInfo createToken(final String userId, final Map<String, ?> attributes)
    {
        // The recommended way to create a token is through TokenManager.create
        return null;
    }

    @Override
    public TokenInfo getTokenInfo(final String loginToken)
    {
        // The login token has the format <nodeUUID> or <nodeUUID>-<secretKey>, extract the node UUID from it
        final String nodeId = StringUtils.substringBefore(loginToken, CardsToken.TOKEN_DELIMITER);
        // Retrieve the node from the repo
        final Tree tokenTree = this.identifierManager.getTree(nodeId);
        // Check that it is a good token node
        if (isValidTokenTree(tokenTree)) {
            // Parse and return the token information
            return new CardsTokenImpl(this.root, tokenTree, loginToken, getUser(tokenTree));
        }
        // Invalid token
        return null;
    }

    /**
     * Check that the provided node is a valid token node, i.e. it has the correct nodetype, and is in the correct place
     * for tokens. Other than these basic checks, this does not actually validate the secret key or expiration date of
     * the actual token.
     *
     * @param tokenNode a JCR node
     * @return {@code true} if the node is a non-null valid node for a token, {@code false} otherwise
     */
    private boolean isValidTokenTree(final Tree tokenTree)
    {
        if (tokenTree == null || !tokenTree.exists()) {
            return false;
        }
        // The expected path is /jcr:system/cards:tokens/<userId>/<tokenNode>
        return tokenTree.getPath().startsWith(CardsToken.TOKENS_NODE_PATH + "/")
            && "cards:Token".equals(TreeUtil.getPrimaryTypeName(tokenTree));
    }

    /**
     * Extract the username that the token authenticates. The actual validity of the username is not checked.
     *
     * @param tokenTree a valid Oak Tree storing a token
     * @return a username
     */
    private String getUser(final Tree tokenTree)
    {
        Tree crt = tokenTree;
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
    }
}
