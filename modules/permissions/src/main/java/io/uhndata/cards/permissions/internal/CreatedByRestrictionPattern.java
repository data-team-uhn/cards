/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.permissions.internal;

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on nodes created by the accessing user.
 *
 * @version $Id$
 */
public class CreatedByRestrictionPattern implements RestrictionPattern
{
    /** A reference to the session that created this pattern. */
    private final Session session;

    /**
     * Constructor which receives the current session of the user activating this restriction.
     *
     * @param session the current session
     */
    public CreatedByRestrictionPattern(final Session session)
    {
        this.session = session;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        // A node without a creator should be allowed through
        if (tree.getProperty("jcr:createdBy") == null) {
            return false;
        }

        // This only applies to the node itself, and only when we have a session
        if (this.session == null || property != null) {
            return false;
        }

        // Check if this user is the one that created this node
        return StringUtils.equals(tree.getProperty("jcr:createdBy").getValue(Type.STRING), this.session.getUserID());
    }

    @Override
    public boolean matches(String path)
    {
        // This method doesn't seem to be called, the one above is used instead
        return false;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }
}
