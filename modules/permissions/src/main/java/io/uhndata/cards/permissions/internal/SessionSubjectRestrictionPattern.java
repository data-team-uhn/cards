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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on a form or subject node if the session is bound to a
 * specific subject, and the accessed node belongs to a form that has the target subject as its subject, is the target
 * subject node itself, or one of its ancestor subjects. Having a subject "bound" to the session means that the session
 * has an attribute named {@code cards:sessionSubject} with a path leading to a subject as its value. The attributes are
 * taken from the credentials used to authenticate when creating the session, so this happens automatically when logging
 * in via a token if the token has such property.
 *
 * @version $Id$
 */
public class SessionSubjectRestrictionPattern implements RestrictionPattern
{
    /** The current session, which may contain a subject identifier. */
    private final Session session;

    /**
     * Constructor passing all the needed information.
     *
     * @param session the current session
     */
    public SessionSubjectRestrictionPattern(final Session session)
    {
        this.session = session;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }

    @Override
    public boolean matches(String path)
    {
        // This is called when a new node is being created
        // Access to the parent node has already been checked, so it's safe to allow new nodes for an accessible parent
        return true;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        if (this.session == null) {
            // No session, no bound subject
            return false;
        }

        final String sessionSubject = (String) this.session.getAttribute("cards:sessionSubject");
        if (StringUtils.isBlank(sessionSubject)) {
            // No bound subject, the access rule does not apply
            return false;
        }

        return isFormForSubject(tree, sessionSubject) || isSubjectOrAncestor(tree, sessionSubject);
    }

    private boolean isFormForSubject(final Tree start, final String sessionSubject)
    {
        final Tree form = findAncestor(start, "cards:Form");
        if (form == null) {
            // Not part of a form
            return false;
        }

        // This is a form. Check if the node has a subject
        final PropertyState subjectProperty = form.getProperty("subject");
        // If there is a subject set, this authorization rule only applies if it is the same as the session's subject
        try {
            return subjectProperty != null && StringUtils.equals(sessionSubject,
                this.session.getNodeByIdentifier(subjectProperty.getValue(Type.STRING)).getPath());
        } catch (RepositoryException e) {
            return false;
        }
    }

    private boolean isSubjectOrAncestor(final Tree start, final String sessionSubject)
    {
        final Tree subject = findAncestor(start, "cards:Subject");
        if (subject == null) {
            // Not part of a subject
            return false;
        }

        // This is a subject, this authorization rule only applies if it is the same as the session's subject or one of
        // its ancestors
        return StringUtils.startsWith(sessionSubject, subject.getPath());
    }

    private Tree findAncestor(final Tree start, final String targetNodetype)
    {
        // This restriction only applies to Forms/Subjects and their descendant items.
        // If this is not a Form or Subject node, look for one among its ancestors.
        Tree current = start;
        while (!current.isRoot() && (current.getProperty("jcr:primaryType") == null
            || !StringUtils.equals(current.getProperty("jcr:primaryType").getValue(Type.STRING), targetNodetype))) {
            current = current.getParent();
        }
        if (current.isRoot()) {
            // Not a targeted node, this restriction doesn't apply
            return null;
        }
        return current;
    }
}
