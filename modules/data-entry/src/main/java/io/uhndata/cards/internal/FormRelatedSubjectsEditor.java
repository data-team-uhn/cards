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
package io.uhndata.cards.internal;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that computes and sets the {@code relatedSubjects} property for every changed Form. The related
 * subjects are the subject that the form belongs to, along with that subject's ancestors.
 *
 * @version $Id$
 */
public class FormRelatedSubjectsEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormRelatedSubjectsEditor.class);

    private static final String PROP_RELATED_SUBJECTS = "relatedSubjects";

    private static final String PROP_SUBJECT = "subject";

    private static final String PROP_PARENTS = "parents";

    private final Session session;

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the current node
     * @param session the current JCR session
     */
    public FormRelatedSubjectsEditor(final NodeBuilder nodeBuilder, final Session session)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.session = session;
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        if (isForm(this.currentNodeBuilder)) {
            // If this is already a form, there's no need to descend further down, there aren't any sub-forms
            return null;
        }
        return new FormRelatedSubjectsEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        if (isForm(this.currentNodeBuilder)) {
            // If this is already a form, there's no need to descend further down, there aren't any sub-forms
            return null;
        }
        return new FormRelatedSubjectsEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public void leave(final NodeState before, final NodeState after) throws CommitFailedException
    {
        if (isForm(this.currentNodeBuilder)) {
            try {
                setRelatedSubjects();
            } catch (final AccessDeniedException | ItemNotFoundException e) {
                // This is an "expected" exception, access to specific subjects may be denied to users
            } catch (final RepositoryException e) {
                // This is not a fatal error, the related subjects is not required for a functional application
                LOGGER.warn("Unexpected exception while computing the related subjects of form {}",
                    this.currentNodeBuilder.getString("jcr:uuid"));
            }
        }
    }

    /**
     * Gather all ancestors-and-self Subjects, starting with the target form's subject, and store them as a multi-valued
     * {@code relatedSubjects} property of the form.
     *
     * @throws RepositoryException if accessing the repository fails
     */
    private void setRelatedSubjects() throws RepositoryException
    {
        final List<String> identifiers = new ArrayList<>();
        Node subjectNode = this.session
            .getNodeByIdentifier(this.currentNodeBuilder.getProperty(PROP_SUBJECT).getValue(Type.REFERENCE));

        // Iterate through all parents of the subject
        while (subjectNode != null) {
            identifiers.add(subjectNode.getProperty("jcr:uuid").getString());
            if (!subjectNode.hasProperty(PROP_PARENTS)) {
                break;
            }
            Value parent;
            if (subjectNode.getProperty(PROP_PARENTS).isMultiple()) {
                parent = subjectNode.getProperty(PROP_PARENTS).getValues()[0];
            } else {
                parent = subjectNode.getProperty(PROP_PARENTS).getValue();
            }
            subjectNode = this.session.getNodeByIdentifier(parent.getString());
        }

        // Write relatedSubjects to the form node
        this.currentNodeBuilder.setProperty(PROP_RELATED_SUBJECTS, identifiers, Type.WEAKREFERENCES);
    }

    /**
     * Checks if the given node is a Form node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Form node, {@code false} otherwise
     */
    private boolean isForm(NodeBuilder node)
    {
        return "cards:Form".equals(getNodeType(node));
    }

    /**
     * Retrieves the primary node type of a node, as a String.
     *
     * @param node the node whose type to retrieve
     * @return a string
     */
    private String getNodeType(NodeBuilder node)
    {
        return node.getProperty("jcr:primaryType").getValue(Type.STRING);
    }
}
