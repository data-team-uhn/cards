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
package io.uhndata.cards.subjects.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Basic utilities for working with Subject data.
 *
 * @version $Id$
 */
@Component
public final class SubjectUtilsImpl extends AbstractNodeUtils implements SubjectUtils
{
    @Reference
    private ThreadResourceResolverProvider rrp;

    // Subject methods

    @Override
    public Node getSubject(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrp));
        return isSubject(result) ? result : null;
    }

    @Override
    public boolean isSubject(final Node node)
    {
        return isNodeType(node, SUBJECT_NODETYPE);
    }

    @Override
    public boolean isSubject(final NodeBuilder node)
    {
        return node == null ? false : isSubject(node.getNodeState());
    }

    @Override
    public boolean isSubject(final NodeState node)
    {
        return isNodeType(node, SUBJECT_NODETYPE, getSession(this.rrp));
    }

    @Override
    public Node getType(final Node subject)
    {
        try {
            if (isSubject(subject) && subject.hasProperty(TYPE_PROPERTY)) {
                return subject.getProperty(TYPE_PROPERTY).getNode();
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
        return null;
    }

    @Override
    public String getLabel(final Node subject)
    {
        try {
            if (isSubject(subject) && subject.hasProperty(LABEL_PROPERTY)) {
                return subject.getProperty(LABEL_PROPERTY).getString();
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
        return null;
    }
}
