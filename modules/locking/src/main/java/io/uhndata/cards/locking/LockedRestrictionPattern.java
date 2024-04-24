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
package io.uhndata.cards.locking;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on nodes created by the accessing user.
 *
 * @version $Id$
 */
public class LockedRestrictionPattern implements RestrictionPattern
{
    /** A reference to the session that created this pattern. */
    private final Session session;

    /**
     * Constructor which receives the current session of the user activating this restriction.
     *
     * @param session the current session
     */
    public LockedRestrictionPattern(final Session session)
    {
        this.session = session;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        if (isSubjectOrForm(tree)) {
            return isLocked(tree) || isParentSubjectLocked(tree);
        }
        return false;
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

    private boolean isLocked(final Tree node)
    {
        PropertyState flags = node.getProperty("statusFlags");
        for (int i = 0; i < flags.count(); ++i) {
            if ("LOCKED".equals(flags.getValue(Type.STRING, i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLocked(final Node node)
        throws RepositoryException
    {
        Value[] flags = node.getProperty("statusFlags").getValues();
        for (int i = 0; i < flags.length; ++i) {
            if ("LOCKED".equals(flags[i].getString())) {
                return true;
            }
        }
        return false;
    }


    private boolean isParentSubjectLocked(final Tree node)
    {
        if (isForm(node)) {
            String subject = node.getProperty("subject").getValue(Type.REFERENCE);
            try {
                return isLocked(this.session.getNodeByIdentifier(subject));
            } catch (RepositoryException e) {
                // User cannot access the subject they are trying to create a form for
                // so cannot check if it is locked
                return true;
            }
        } else {
            // Must be a subject
            Tree parent = node.getParent();
            return isSubject(parent) && isLocked(parent);
        }
    }

    private boolean isSubjectOrForm(final Tree node)
    {
        return isForm(node) || isSubject(node);
    }

    private boolean isForm(final Tree node)
    {
        return node.hasProperty("jcr:primaryType")
            && "cards:Form".equals(node.getProperty("jcr:primaryType").getValue(Type.STRING));
    }

    private boolean isSubject(final Tree node)
    {
        return node.hasProperty("jcr:primaryType")
            && "cards:Subject".equals(node.getProperty("jcr:primaryType").getValue(Type.STRING));
    }
}
