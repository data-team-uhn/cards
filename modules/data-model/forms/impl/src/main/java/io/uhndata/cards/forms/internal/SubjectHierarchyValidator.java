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
package io.uhndata.cards.forms.internal;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * A {@link Validator} that ensures that a child Subject has a parent of required type.
 *
 * @version $Id$
 */
public class SubjectHierarchyValidator extends DefaultValidator
{
    private final SubjectTypeUtils subjectTypeUtils;

    private final SubjectUtils subjectUtils;

    private Deque<NodeState> parentNodes = new LinkedList<>();

    public SubjectHierarchyValidator(final SubjectTypeUtils subjectTypeUtils, final SubjectUtils subjectUtils)
    {
        this.subjectTypeUtils = subjectTypeUtils;
        this.subjectUtils = subjectUtils;
    }

    @Override
    public void enter(NodeState before, NodeState after) throws CommitFailedException
    {
        this.parentNodes.addLast(after);
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
        this.parentNodes.removeLast();
    }

    @Override
    public Validator childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return this;
    }

    @Override
    public Validator childNodeAdded(final String name, final NodeState after) throws CommitFailedException
    {
        this.subjectUtils.isSubject(after);
        // Get the type of this node. Return immediately if it's not a cards:Subject node
        final String childNodeType = after.getName("jcr:primaryType");
        if (SubjectUtils.SUBJECT_NODETYPE.equals(childNodeType)) {
            checkSubjectHierarchy(after);
        }
        return this;
    }

    /**
     * Check that a subject has all the required ancestors matching its subject type hierarchy. If the hierarchy is not
     * respected, a {@code CommitFailedException} is raised.
     *
     * @param subject the node to check
     * @throws CommitFailedException if the subject doesn't have all the required ancestors
     */
    private void checkSubjectHierarchy(NodeState subject) throws CommitFailedException
    {
        try {
            // Get the subject's type
            Node currentParentType = this.subjectTypeUtils.getSubjectType(
                subject.getProperty("type").getValue(Type.REFERENCE));
            // Get the subject's hierarchy in reverse order, from the subject up to the root
            final Iterator<NodeState> parents = this.parentNodes.descendingIterator();
            // Compare the type hierarchy and subject hierarchy to check that the right subject type is present among
            // the ancestors at the right level
            while (this.subjectTypeUtils.isSubjectType(currentParentType)) {
                final String requiredParentTypeUuid = currentParentType.getProperty("parents").getString();
                if (parents.hasNext()) {
                    NodeState parent = parents.next();
                    if (this.subjectUtils.isSubject(parent) && parent.getProperty("type")
                        .getValue(Type.REFERENCE).equals(requiredParentTypeUuid)) {
                        currentParentType = currentParentType.getParent();
                        continue;
                    }
                }
                throw new CommitFailedException(CommitFailedException.STATE, 400,
                    "This subject cannot be created without parent");
            }
        } catch (RepositoryException e) {
            // Should not happen
        }
    }
}
