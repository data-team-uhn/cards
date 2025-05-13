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
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.locking.api.LockException;
import io.uhndata.cards.locking.api.LockWarning;
import io.uhndata.cards.locking.spi.LockPrecondition;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * A {@code LockPrecondition} that throws a warning if an incomplete form would be locked.
 *
 * @version $Id$
 */
@Component
public class IncompleteFormsPrecondition implements LockPrecondition
{
    private static final String NAME = "IncompleteForms";

    private static final Logger LOGGER = LoggerFactory.getLogger(IncompleteFormsPrecondition.class);

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Override
    public boolean canLock(Node node) throws LockWarning, LockException
    {
        try {
            if (this.subjectUtils.isSubject(node)) {
                checkSubject(node);
            } else if (this.formUtils.isForm(node)) {
                checkForm(node);
            }
            // Other node types cannot contain incomplete forms
            return true;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error checking for incomplete forms", e);
            throw new LockException("Unable to check for incomplete forms");
        }
    }

    // Check this subject and any child subjects or forms
    private void checkSubject(Node subject)
        throws RepositoryException, LockWarning, LockException
    {
        final NodeIterator childNodes = subject.getNodes();
        while (childNodes.hasNext()) {
            Node childNode = childNodes.nextNode();
            if (this.subjectUtils.isSubject(childNode)) {
                canLock(childNode);
            }
        }

        final PropertyIterator references = subject.getReferences("subject");
        while (references.hasNext()) {
            Node referenceNode = references.nextProperty().getParent();
            if (this.formUtils.isForm(referenceNode)) {
                canLock(referenceNode);
            }
        }
    }

    // Check to see if this form is incomplete
    private void checkForm(Node form)
        throws RepositoryException, LockWarning
    {
        // Check if the form is incomplete
        if (form.hasProperty("statusFlags")) {
            for (Value value : form.getProperty("statusFlags").getValues()) {
                if ("INCOMPLETE".equals(value.getString())) {
                    throw new LockWarning("Incomplete form detected");
                }
            }
        }
    }

    @Override
    public String getName()
    {
        return NAME;
    }
}
