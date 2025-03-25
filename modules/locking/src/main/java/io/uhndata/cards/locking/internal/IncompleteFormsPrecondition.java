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
package io.uhndata.cards.locking.internal;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.locking.api.LockError;
import io.uhndata.cards.locking.api.LockWarning;
import io.uhndata.cards.locking.spi.LockPrecondition;

@Component
public class IncompleteFormsPrecondition implements LockPrecondition
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockManagerImpl.class);

    private static final String SUBJECT_NODE_TYPE = "cards:Subject";
    private static final String FORM_NODE_TYPE = "cards:Form";

    @Override
    public boolean canLock(Node node) throws LockWarning, LockError
    {
        try {
            if (node.isNodeType(SUBJECT_NODE_TYPE)) {
                checkSubject(node);
            } else if (node.isNodeType(FORM_NODE_TYPE)) {
                checkForm(node);
            }
            return true;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error checking for incomplete forms", e);
            throw new LockError("Unable to check for incomplete forms");
        }
    }

    private void checkSubject(Node node)
        throws RepositoryException, LockError, LockWarning
    {
        final NodeIterator childNodes = node.getNodes();
        while (childNodes.hasNext()) {
            Node childNode = childNodes.nextNode();
            if (childNode.isNodeType(SUBJECT_NODE_TYPE)) {
                canLock(childNode);
            }
        }

        final PropertyIterator references = node.getReferences();
        while (references.hasNext()) {
            Node referenceNode = references.nextProperty().getParent();
            if (referenceNode.isNodeType(FORM_NODE_TYPE)) {
                canLock(referenceNode);
            }
        }
    }

    private void checkForm(Node node)
        throws RepositoryException, LockWarning
    {
        // Check if the form is incomplete
        if (node.hasProperty("statusFlags")) {
            for (Value value : node.getProperty("statusFlags").getValues()) {
                if ("INCOMPLETE".equals(value.getString())) {
                    throw new LockWarning("Incomplete form detected");
                }
            }
        }
    }
}
