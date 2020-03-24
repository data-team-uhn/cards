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
package ca.sickkids.ccm.lfs.formcompletionstatus;

import java.util.ArrayList;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Editor} that verifies the correctness and completeness of
 * submitted questionnaire answers and sets the INVALID and INCOMPLETE
 * status flags accordingly.
 *
 * @version $Id$
 */
public class AnswerCompletionStatusEditor extends DefaultEditor
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerCompletionStatusEditor.class);

    private static final String STATUS_FLAGS = "statusFlags";
    private static final String STATUS_FLAG_INCOMPLETE = "INCOMPLETE";
    private static final String STATUS_FLAG_INVALID = "INVALID";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    // A ResourceResolver object is passed in during the initialization of this object. This ResourceResolver
    // is later used for obtaining the constraints on the answers submitted to a question.
    private final ResourceResolver currentResourceResolver;

    private final String currentPath;

    private final ArrayList<NodeBuilder> currentNodeBuilderPath;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param resourceResolver a ResourceResolver object used to obtain answer constraints
     * @param path the JCR path of this node
     */
    public AnswerCompletionStatusEditor(ArrayList<NodeBuilder> nodeBuilder, ResourceResolver resourceResolver,
        String path)
    {
        this.currentNodeBuilderPath = nodeBuilder;
        this.currentNodeBuilder = nodeBuilder.get(nodeBuilder.size() - 1);
        this.currentResourceResolver = resourceResolver;
        this.currentPath = path;
        LOGGER.warn("Constructing AnswerCompletionStatusEditor with path: {}", path);
        LOGGER.warn("this.currentNodeBuilderPath = {}", this.currentNodeBuilderPath);
        /*
        try {
            summarizeBuilders(this.currentNodeBuilderPath);
        } catch (RepositoryException e) {
            LOGGER.warn("Could not run summarize()");
        }
        */
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        propertyChanged(null, after);
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        Node questionNode = getQuestionNode(this.currentNodeBuilder);
        if (questionNode != null && "value".equals(after.getName())) {
            Iterable<String> nodeAnswers = after.getValue(Type.STRINGS);
            int numAnswers = iterableLength(nodeAnswers);
            ArrayList<String> statusFlags = new ArrayList<String>();
            if (checkInvalidAnswer(questionNode, numAnswers)) {
                statusFlags.add(STATUS_FLAG_INVALID);
                statusFlags.add(STATUS_FLAG_INCOMPLETE);
            } else {
                /*
                 * We are here because:
                 *     - minAnswers == 0 && maxAnswers == 0
                 *     - minAnswers == 0 && maxAnswers == 1 && numAnswers in range [0,1] (eg. optional radio button)
                 *     - minAnswers == 1 && maxAnswers == 0 && numAnswers in range [1,+INF) (eg. mandatory checkboxes)
                 *     - minAnswers == 1 && maxAnswers == 1 && numAnswers == 1 (eg. mandatory radio button)
                 *     - minAnswers == N && maxAnswers == 0 && numAnswers in range [N,+INF)
                 *         (eg. at least N (inclusive) checkboxes must be selected)
                 *     - minAnswers == 0 && maxAnswers == M && numAnswers in range [0, M]
                 *         (eg. at most M (inclusive) checkboxes must be selected)
                 *     - minAnswers == N && maxAnswers == M && numAnswers in range [N,M]
                 *         (eg. between N (inclusive) and M (inclusive) checkboxes must be selected)
                 */

                /*
                 * TODO: Implement validation rules and check them here
                 * Remove INVALID and INCOMPLETE flags if all validation rules pass
                 */
            }
            this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            //Summarize all parents
            for (int i = this.currentNodeBuilderPath.size() - 1; i >= 0; i--) {
                String[] sections = this.currentPath.split("/");
                String subsection = "";
                //ArrayList<NodeBuilder> subsectionNodeBuilder = new ArrayList<NodeBuilder>();
                for (int j = 0; j <= i; j++) {
                    if ("".equals(sections[j])) {
                        continue;
                    }
                    subsection += "/" + sections[j];
                    //subsectionNodeBuilder.add(this.currentNodeBuilderPath.get(j));
                }
                if (!subsection.startsWith("/Forms/")) {
                    continue;
                }
                LOGGER.warn("WILL SUMMARIZE: {}", subsection);
                LOGGER.warn("OBJECTS...: {}", this.currentNodeBuilderPath.get(i));
                try {
                    summarizeBuilders(this.currentNodeBuilderPath);
                } catch (RepositoryException e) {
                    LOGGER.warn("Could not run summarize()");
                }
            }
        }
    }

    // Called when a property is deleted
    @Override
    public void propertyDeleted(PropertyState before) throws CommitFailedException
    {
        Node questionNode = getQuestionNode(this.currentNodeBuilder);
        if (questionNode != null) {
            if ("value".equals(before.getName())) {
                ArrayList<String> statusFlags = new ArrayList<String>();
                //Only add the INVALID,INCOMPLETE flags if the given question requires more than zero answers
                if (checkInvalidAnswer(questionNode, 0)) {
                    statusFlags.add(STATUS_FLAG_INVALID);
                    statusFlags.add(STATUS_FLAG_INCOMPLETE);
                }
                this.currentNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            }
        }
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        LOGGER.warn("[AnswerCompletionStatusEditor] childNodeAdded: {}", name);
        Node questionNode = getQuestionNode(this.currentNodeBuilder.getChildNode(name));
        if (questionNode != null) {
            if (this.currentNodeBuilder.getChildNode(name).hasProperty("question")) {
                ArrayList<String> statusFlags = new ArrayList<String>();
                //Only add the INCOMPLETE flag if the given question requires more than zero answers
                if (checkInvalidAnswer(questionNode, 0)) {
                    statusFlags.add(STATUS_FLAG_INCOMPLETE);
                }
                this.currentNodeBuilder.getChildNode(name).setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
            }
        }
        String newNodeName = "";
        if (!"".equals(name)) {
            newNodeName = this.currentPath + "/" + name;
        }
        ArrayList<NodeBuilder> tmpList = new ArrayList<NodeBuilder>();
        for (int i = 0; i < this.currentNodeBuilderPath.size(); i++) {
            tmpList.add(this.currentNodeBuilderPath.get(i));
        }
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver, newNodeName);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        LOGGER.warn("[AnswerCompletionStatusEditor] childNodeChanged: {}", name);
        String newNodeName = "";
        if (!"".equals(name)) {
            newNodeName = this.currentPath + "/" + name;
        }
        ArrayList<NodeBuilder> tmpList = new ArrayList<NodeBuilder>();
        for (int i = 0; i < this.currentNodeBuilderPath.size(); i++) {
            tmpList.add(this.currentNodeBuilderPath.get(i));
        }
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver, newNodeName);
    }

    /**
     * Gets the question node associated with the answer for which this
     * AnswerCompletionStatusEditor is an editor thereof.
     *
     * @return the question Node object associated with this answer
     */
    private Node getQuestionNode(NodeBuilder nb)
    {
        try {
            if (nb.hasProperty("question")) {
                Session resourceSession = this.currentResourceResolver.adaptTo(Session.class);
                String questionNodeReference = nb.getProperty("question").getValue(Type.REFERENCE);
                Node questionNode = resourceSession.getNodeByIdentifier(questionNodeReference);
                return questionNode;
            }
        } catch (RepositoryException ex) {
            return null;
        }
        return null;
    }

    /**
     * Counts the number of items in an Iterable.
     *
     * @param iterable the Iterable object to be counted
     * @return the number of objects in the Iterable
     */
    private int iterableLength(Iterable iterable)
    {
        int len = 0;
        Iterator iterator = iterable.iterator();
        while (iterator.hasNext())
        {
            iterator.next();
            len++;
        }
        return len;
    }

    /**
     * Reports if a given number of answers is invalid for a given question.
     *
     * @param questionNode the Node to provide the minAnswers and maxAnswers properties
     * @return true if the number of answers is valid, false if it is not
     */
    private boolean checkInvalidAnswer(Node questionNode, int numAnswers)
    {
        try {
            long minAnswers = questionNode.getProperty("minAnswers").getLong();
            long maxAnswers = questionNode.getProperty("maxAnswers").getLong();
            if ((numAnswers < minAnswers && minAnswers != 0) || (numAnswers > maxAnswers && maxAnswers != 0)) {
                return true;
            }
        } catch (RepositoryException ex) {
            //If something goes wrong then we definitely cannot have a valid answer
            return true;
        }
        return false;
    }

    private void summarizeBuilders(ArrayList<NodeBuilder> nodeBuilders) throws RepositoryException
    {
        if (nodeBuilders.size() < 3) {
            return;
        }
        NodeBuilder selectedNodeBuilder = nodeBuilders.get(nodeBuilders.size() - 2);
        LOGGER.warn("WORKING WITH: {}", selectedNodeBuilder);
        //Iterate through all children of this node
        //NodeIterator childNodes = selectedNode.getNodes();
        Iterable<String> childrenNames = selectedNodeBuilder.getChildNodeNames();
        Iterator<String> childrenNamesIter = childrenNames.iterator();
        boolean isInvalid = false;
        boolean isIncomplete = false;
        while (childrenNamesIter.hasNext()) {
            String selectedChildName = childrenNamesIter.next();
            NodeBuilder selectedChild = selectedNodeBuilder.getChildNode(selectedChildName);
            //Is selectedChild - invalid? , incomplete?
            if (selectedChild.hasProperty(STATUS_FLAGS)) {
                Iterable<String> selectedProps = selectedChild.getProperty(STATUS_FLAGS).getValue(Type.STRINGS);
                Iterator<String> selectedPropsIter = selectedProps.iterator();
                while (selectedPropsIter.hasNext()) {
                    String thisStr = selectedPropsIter.next();
                    LOGGER.warn("...checking for... {}", thisStr);
                    if (STATUS_FLAG_INVALID.equals(thisStr)) {
                        isInvalid = true;
                    }
                    if (STATUS_FLAG_INCOMPLETE.equals(thisStr)) {
                        isIncomplete = true;
                    }
                }
            }
        }
        //Set the flags in selectedNode accordingly
        ArrayList<String> statusFlags = new ArrayList<String>();
        if (isInvalid) {
            statusFlags.add(STATUS_FLAG_INVALID);
        }
        if (isIncomplete) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        }
        //Write these statusFlags to the JCR repo
        //selectedNode.setProperty(STATUS_FLAGS, statusFlags.toArray(new String[0]));
        selectedNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }
}
