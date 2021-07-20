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
package io.uhndata.cards.permissions.internal.ownership;

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on a node of type Form or Subject if it has an Owner
 * specified, and the current user is that owner.
 *
 * @version $Id$
 */
public class OwnerRestrictionPattern implements RestrictionPattern
{
    private final Session session;

    /**
     * Default constructor, passing the needed {@link #session}.
     *
     * @param session the current user session
     */
    public OwnerRestrictionPattern(Session session)
    {
        this.session = session;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        if (this.session == null) {
            return false;
        }
        // This restriction only applies to Forms/Subjects and their descendant items.
        // If this is not a Form or Subject node, look for one among its ancestors.
        Tree mainTree = tree;
        while (!mainTree.isRoot() && (mainTree.getProperty("jcr:primaryType") == null
            || !StringUtils.equalsAny(mainTree.getProperty("jcr:primaryType").getValue(Type.STRING),
                "cards:Form", "cards:Subject"))) {
            mainTree = mainTree.getParent();
        }
        if (mainTree.isRoot()) {
            // Not a targeted node, this restriction doesn't apply
            return true;
        }

        // Check if the node has an owner
        PropertyState ownerProperty = mainTree.getProperty("owner");
        if (ownerProperty == null || StringUtils.isBlank(ownerProperty.getValue(Type.STRING))) {
            // There is no owner, we let the rule apply since otherwise there is no way to create/access a node before
            // setting an owner -- chicken vs egg problem
            // FIXME Maybe check the jcr:createdBy property in this case?
            return true;
        }

        // There is an owner set, so this authorization rule only applies to that specified owner
        return StringUtils.equals(ownerProperty.getValue(Type.STRING), this.session.getUserID());

    }

    @Override
    public boolean matches(String path)
    {
        // This is called when a new node is being created.
        // Either the node is a new targeted resource such as a Form,
        // in which case it doesn't yet have an owner and access should be allowed,
        // or it is a child node of an existing resource,
        // in which case the access rights have already been checked on the parent resource.
        //
        // The other possible scenario is that someone tries to create a different kind of resource,
        // which shouldn't be allowed, but it is impossible to enforce that restriction here,
        // since we don't yet know the type of the new node being created.
        return true;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }
}
