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
    private ThreadLocal<LinkedList<NodeState>> parentNodes = new ThreadLocal<>();

    public SubjectHierarchyValidator(SubjectTypeUtils subjectTypeUtils)
    {
        this.subjectTypeUtils = subjectTypeUtils;
    }

    @Override
    public Validator childNodeAdded(final String name, final NodeState after) throws CommitFailedException
    {
        this.parentNodes.get().add(after);
        // Get the type of this node. Return immediately if it's not a cards:Subject node
        final String childNodeType = after.getName("jcr:primaryType");
        if (!(SubjectUtils.SUBJECT_NODETYPE).equals(childNodeType)) {
            return new SubjectHierarchyValidator(this.subjectTypeUtils);
        }

        return isFormValid(after);
    }

    public Validator isFormValid(NodeState after) throws CommitFailedException
    {
        try {
            // Get the type of child Subject node as a node
            Node currentParentType = this.subjectTypeUtils.getSubjectType(after.getProperty("type")
                    .getValue(Type.REFERENCE));
            // Get the descendingIterator from the deque of parents
            final Iterator<NodeState> currentParentNodesIterator = this.parentNodes.get().descendingIterator();
            while (currentParentType.getPrimaryNodeType().getName().equals(SubjectTypeUtils.SUBJECT_TYPE_NODETYPE)) {

                final String requiredParentTypeUuid = currentParentType.getProperty("parents").getString();

                if (currentParentNodesIterator.hasNext() && currentParentNodesIterator.next().getProperty("type")
                        .getValue(Type.REFERENCE).equals(requiredParentTypeUuid)) {
                    currentParentType = currentParentType.getParent();
                } else {
                    throw new CommitFailedException(CommitFailedException.STATE, 400,
                            "This subject cannot be created without parent");
                }
            }
        } catch (RepositoryException e) {
            // Should not happen
        }
        return new SubjectHierarchyValidator(this.subjectTypeUtils);
    }
}
