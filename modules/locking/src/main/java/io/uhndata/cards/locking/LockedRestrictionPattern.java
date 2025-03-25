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

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that matches locked subjects, forms and their children.
 *
 * @version $Id$
 */
public class LockedRestrictionPattern implements RestrictionPattern
{
    /** A reference to the session that created this pattern. */
    protected final Session session;

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
    public boolean matches(String path)
    {
        // This method doesn't seem to be called, instead matches(Tree tree) is used
        return false;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        Tree currentTree = tree;
        while (!currentTree.isRoot())
        {
            if (isSubjectOrForm(currentTree)) {
                String lock = currentTree.hasProperty("cards:Lock")
                    ? currentTree.getProperty("cards:Lock").getValue(Type.REFERENCE)
                    : null;
                return lock != null && lock.length() > 0;
            }
            currentTree = currentTree.getParent();
        }
        return false;
    }

    protected static boolean isSubjectOrForm(final Tree node)
    {
        return isForm(node) || isSubject(node);
    }

    protected static boolean isForm(final Tree node)
    {
        return node.hasProperty("jcr:primaryType")
            && "cards:Form".equals(node.getProperty("jcr:primaryType").getValue(Type.STRING));
    }

    protected static boolean isSubject(final Tree node)
    {
        return node.hasProperty("jcr:primaryType")
            && "cards:Subject".equals(node.getProperty("jcr:primaryType").getValue(Type.STRING));
    }
}
