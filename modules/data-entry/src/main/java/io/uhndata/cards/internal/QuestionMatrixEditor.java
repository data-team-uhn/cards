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

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;


/**
 * An {@link Editor} that sets (copies) the {@code QuestionMatrix} question-related properties and children
 * into every {@code Question} child node. The related props are: minAnswers, maxAnswers, dataType;
 * children are list of predefined {@code AnswerOption} options.
 *
 * Editor is invoked when {@code AnswerOption} child node or property of {@code QuestionMatrix} is
 * added/modified/deleted or when new {@code Question} child is added to the {@code QuestionMatrix} node.
 *
 * @version $Id$
 */
public class QuestionMatrixEditor extends DefaultEditor
{
    private final Session session;

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final boolean isQuestionMatrixNode;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the current node
     * @param session the session used to retrieve subjects by UUID
     */
    public QuestionMatrixEditor(final NodeBuilder nodeBuilder, final Session session)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.session = session;
        this.isQuestionMatrixNode = isQuestionMatrix(this.currentNodeBuilder);
    }

    @Override
    public void propertyAdded(PropertyState after)
    {
        propertyChanged(null, after);
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after)
    {
        if (!this.isQuestionMatrixNode) {
            return;
        }

        String propName = after.getName();
        if (StringUtils.equalsAny(propName, "minAnswers", "maxAnswers", "dataType")) {

            // Update changed property in each Question child node
            for (String childNodeName : this.currentNodeBuilder.getChildNodeNames()) {
                NodeBuilder childNode = this.currentNodeBuilder.getChildNode(childNodeName);
                if (isQuestion(childNode)) {
                    if (StringUtils.equals(propName, "dataType")) {
                        childNode.setProperty(propName, after.getValue(Type.STRING));
                    } else {
                        childNode.setProperty(propName, after.getValue(Type.LONG));
                    }
                    childNode.setProperty("displayMode", "list", Type.STRING);
                }
            }
        }
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        if (this.isQuestionMatrixNode) {
            NodeBuilder childNode = this.currentNodeBuilder.getChildNode(name);

            for (String childNodeName : this.currentNodeBuilder.getChildNodeNames()) {
                NodeBuilder qNode = this.currentNodeBuilder.getChildNode(childNodeName);

                // Answer Option is added -> copy this option to every question node
                if (isAnswerOption(childNode) && isQuestion(qNode)) {
                    copyOptionNode(name, childNode, qNode);
                // Question is added -> copy all options to this question node
                } else if (isQuestion(childNode) && isAnswerOption(qNode)) {
                    copyOptionNode(childNodeName, qNode, childNode);
                }
            }
            // If this is already a QuestionMatrix, there's no need to descend further down
            return null;
        }

        return new QuestionMatrixEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        if (this.isQuestionMatrixNode) {
            NodeBuilder childNode = this.currentNodeBuilder.getChildNode(name);
            // if Answer Option is changed
            if (isAnswerOption(childNode)) {
                for (String childNodeName : this.currentNodeBuilder.getChildNodeNames()) {
                    NodeBuilder qNode = this.currentNodeBuilder.getChildNode(childNodeName);
                    // Remove and copy changed Answer option to every question node
                    if (isQuestion(qNode)) {
                        qNode.getChildNode(name).remove();
                        copyOptionNode(name, childNode, qNode);
                    }
                }
            }
            // If this is already a QuestionMatrix, there's no need to descend further down
            return null;
        }

        return new QuestionMatrixEditor(this.currentNodeBuilder.getChildNode(name), this.session);
    }

    @Override
    public Editor childNodeDeleted(String name, NodeState before)
    {
        if (this.isQuestionMatrixNode) {
            if (isAnswerOption(before)) {
                // delete this option from every question node
                for (String childNodeName : this.currentNodeBuilder.getChildNodeNames()) {
                    NodeBuilder qNode = this.currentNodeBuilder.getChildNode(childNodeName);
                    if (isQuestion(qNode)) {
                        qNode.getChildNode(name).remove();
                    }
                }
            }
            // If this is already a QuestionMatrix, there's no need to descend further down
            return null;
        }

        return new QuestionMatrixEditor(this.currentNodeBuilder.getChildNode(name), this.session);

    }

    private void copyOptionNode(final String name, final NodeBuilder source, final NodeBuilder destination)
    {
        NodeBuilder newNode = destination.setChildNode(name);
        String type = source.getProperty("jcr:primaryType").getValue(Type.STRING);
        newNode.setProperty("jcr:primaryType", type, Type.STRING);
        String sType = source.getProperty("sling:resourceSuperType").getValue(Type.STRING);
        newNode.setProperty("sling:resourceSuperType", sType, Type.STRING);
        if (source.hasProperty("label")) {
            String label = source.getProperty("label").getValue(Type.STRING);
            newNode.setProperty("label", label, Type.STRING);
        }
        if (source.hasProperty("value")) {
            String label = source.getProperty("value").getValue(Type.STRING);
            newNode.setProperty("value", label, Type.STRING);
        }
        if (source.hasProperty("defaultOrder")) {
            String label = source.getProperty("defaultOrder").getValue(Type.STRING);
            newNode.setProperty("defaultOrder", label, Type.STRING);
        }
        if (source.hasProperty("notApplicable")) {
            Boolean notApplicable = source.getProperty("notApplicable").getValue(Type.BOOLEAN);
            newNode.setProperty("notApplicable", notApplicable, Type.BOOLEAN);
        }
    }

    /**
     * Checks if the given node is a QuestionMatrix node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a QuestionMatrix node, {@code false} otherwise
     */
    private boolean isQuestionMatrix(NodeBuilder node)
    {
        return node.exists() && "cards:Section".equals(getNodeType(node)) && node.hasProperty("displayMode")
            && "matrix".equals(node.getProperty("displayMode").getValue(Type.STRING));
    }

    /**
     * Checks if the given node is a Question node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a Question node, {@code false} otherwise
     */
    private boolean isQuestion(NodeBuilder node)
    {
        return node.exists() && "cards:Question".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is a AnswerOption node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a AnswerOption node, {@code false} otherwise
     */
    private boolean isAnswerOption(NodeBuilder node)
    {
        return node.exists() && "cards:AnswerOption".equals(getNodeType(node));
    }

    /**
     * Checks if the given node is a AnswerOption node.
     *
     * @param node the JCR Node to check
     * @return {@code true} if the node is a AnswerOption node, {@code false} otherwise
     */
    private boolean isAnswerOption(NodeState node)
    {
        return node.exists() && "cards:AnswerOption".equals(getNodeType(node));
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

    /**
     * Retrieves the primary node type of a node, as a String.
     *
     * @param node the node whose type to retrieve
     * @return a string
     */
    private String getNodeType(NodeState node)
    {
        return node.getProperty("jcr:primaryType").getValue(Type.STRING);
    }
}
