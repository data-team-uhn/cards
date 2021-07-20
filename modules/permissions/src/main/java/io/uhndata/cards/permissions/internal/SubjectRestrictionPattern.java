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

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A restriction that makes a permissions entry only be valid on a node of type Form for a specific Subject.
 *
 * @version $Id$
 */
public class SubjectRestrictionPattern implements RestrictionPattern
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectRestrictionFactory.class);

    private final String targetSubject;
    private final Session session;

    /**
     * Constructor which receives the configured restriction.
     *
     * @param value the identifier (UUID) of a specific subject
     * @param session the session used to retrieve subjects by UUID
     */
    public SubjectRestrictionPattern(String value, Session session)
    {
        this.targetSubject = value;
        this.session = session;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        // This restriction only applies to Forms and their descendant items.
        // If this is not a Form node, look for one among its ancestors.
        Tree formTree = tree;
        while ((formTree.getProperty("jcr:primaryType") == null
            || !formTree.getProperty("jcr:primaryType").getValue(Type.STRING).equals("cards:Form"))
            && !formTree.isRoot()) {
            formTree = formTree.getParent();
        }
        if (formTree.isRoot()) {
            // Not a Form node, this restriction doesn't apply
            return false;
        }

        // Check if the form's subject is the same as the one specified in the restriction
        String subject = formTree.getProperty("subject").getValue(Type.REFERENCE);
        return matchesReference(subject);
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

    /**
     * Iteratively search a subject's reference for a match to the {@code targetSubject}.
     * @param uuid the first subject to check the reference of
     * @return true if the target was in the chain of references, false if an error occurs or target is not found
     */
    private boolean matchesReference(String uuid)
    {
        if (StringUtils.equals(uuid, this.targetSubject)) {
            return true;
        }

        if (this.session == null) {
            LOGGER.warn("Could not match subject UUID {}: session not found.", uuid);
            return false;
        }

        String nextUuid = uuid;
        try {
            Node subject = this.session.getNodeByIdentifier(nextUuid);
            while (subject.hasProperty("parents")) {
                nextUuid = subject.getProperty("parents").getString();
                if (StringUtils.equals(nextUuid, this.targetSubject)) {
                    return true;
                }
                subject = this.session.getNodeByIdentifier(nextUuid);
            }
        } catch (ItemNotFoundException e) {
            LOGGER.debug("Subject UUID {} is inaccessible", nextUuid, e);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to find subject UUID {}", nextUuid, e);
        }
        return false;
    }
}
