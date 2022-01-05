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
package io.uhndata.cards.auth.token.impl;

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on a form or subject node if the authentication is tied to
 * a specific visit, which is what token-based authentication does, and that visit is the subject of the form, the Visit
 * subject itself, or the parent Patient subject of the Visit.
 *
 * @version $Id$
 */
public class PatientVisitRestrictionPattern implements RestrictionPattern
{
    /** The current session, which may contain a visit identifier. */
    private final Session session;

    /**
     * Constructor passing all the needed information.
     *
     * @param session the current session
     */
    public PatientVisitRestrictionPattern(final Session session)
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
            // No session, no visit
            return false;
        }

        final String sessionVisit = (String) this.session.getAttribute("cards:visit");
        if (StringUtils.isBlank(sessionVisit)) {
            // No visit identifier, the access rule does not apply
            return false;
        }

        return isFormForVisit(tree, sessionVisit) || isSubjectForVisit(tree, sessionVisit);
    }

    private boolean isFormForVisit(final Tree start, final String sessionVisit)
    {
        final Tree form = findAncestor(start, "cards:Form");
        if (form == null) {
            return false;
        }

        // This is a form. Check if the node has a subject
        final PropertyState subjectProperty = form.getProperty("subject");
        // There is a subject set, this authorization rule only applies if the subject
        // is the same as the target visit
        return subjectProperty != null && StringUtils.equals(subjectProperty.getValue(Type.STRING), sessionVisit);
    }

    private boolean isSubjectForVisit(final Tree start, final String sessionVisit)
    {
        final Tree subject = findAncestor(start, "cards:Subject");
        if (subject == null) {
            return false;
        }

        // This is a subject. Check if the node is the right subject.
        final PropertyState uuidProperty = subject.getProperty("jcr:uuid");
        // There is a subject set, this authorization rule only applies if the subject
        // is the same as the target visit
        boolean result = uuidProperty != null && StringUtils.equals(uuidProperty.getValue(Type.STRING), sessionVisit);
        if (!result) {
            // We also want to allow access to the Patient subject that is parent for the visit
            // Look for the target visit among the children of the current subject
            for (Tree child : subject.getChildren()) {
                if (child.hasProperty("jcr:uuid")
                    && StringUtils.equals(child.getProperty("jcr:uuid").getValue(Type.STRING), sessionVisit)) {
                    return true;
                }
            }
        }
        return result;
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
