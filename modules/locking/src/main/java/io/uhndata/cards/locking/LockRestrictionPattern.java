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

// import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.sling.api.resource.ResourceResolverFactory;

/**
 * A restriction that makes locked nodes and forms not editable.
 *
 * @version $Id$
 */
public class LockRestrictionPattern extends AbstractLockingPattern
{
    /**
     * Constructor which receives the current session of the user activating this restriction.
     *
     * @param session the current session
     * @param rrf a resource resolver factory that can be used to get a service session
     */
    public LockRestrictionPattern(final Session session, final ResourceResolverFactory rrf)
    {
        super(session);
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        if (isSubjectOrForm(tree)) {
            boolean locked = isLocked(tree);

            // Can check if the status flags have been modified:
            boolean flagsModified = Tree.Status.MODIFIED.equals(tree.getPropertyStatus("statusFlags"));
            // Cannot directly check what the previous value of the flags was

            try {

                // Can't check the node using the session: causes a stack overflow as trying to access the node
                // triggers a loop of restriction checks and a stack overflow

                // Node node = this.session.getNode(tree.getPath());
                // boolean nodeLocked = isLocked(node);
                // if (nodeLocked != locked) {
                //     return true;
                // }

                // Can't check the node using version history: causes a stack overflow as trying to access the node
                // triggers a loop of restriction checks and a stack overflow

                // Node history = session.getWorkspace().getVersionManager().getBaseVersion(tree.getPath())
                //     .getFrozenNode();
                // boolean historyLocked = isLocked(history);
                // if (locked != historyLocked) {
                //     return true;
                // }


                return false;
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
        return false;
    }
}
