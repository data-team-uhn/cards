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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;

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

    // This holds a list of NodeBuilders with the first item corresponding to the root of the JCR tree
    // and the last item corresponding to the current node. By keeping this list, one is capable of
    // moving up the tree and setting status flags of ancestor nodes based on the status flags of a
    // descendant node.
    private final ArrayList<NodeBuilder> currentNodeBuilderPath;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder an ArrayList of NodeBuilder objects starting from the root of the JCR tree
     *     and moving down towards the current node.
     * @param resourceResolver a ResourceResolver object used to obtain answer constraints
     */
    public AnswerCompletionStatusEditor(ArrayList<NodeBuilder> nodeBuilder, ResourceResolver resourceResolver)
    {
        this.currentNodeBuilderPath = nodeBuilder;
        this.currentNodeBuilder = nodeBuilder.get(nodeBuilder.size() - 1);
        this.currentResourceResolver = resourceResolver;
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        propertyChanged(null, after);
        //Summarize all parents
        try {
            summarizeBuilders(this.currentNodeBuilderPath);
        } catch (RepositoryException e) {
            return;
        }
    }

    // Called when the value of an existing property gets changed
    @Override
    @SuppressWarnings("MultipleStringLiterals")
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
            try {
                summarizeBuilders(this.currentNodeBuilderPath);
            } catch (RepositoryException e) {
                LOGGER.warn("Could not run summarize()");
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

                //Summarize all parents
                try {
                    summarizeBuilders(this.currentNodeBuilderPath);
                } catch (RepositoryException e) {
                    LOGGER.warn("Could not run summarize()");
                }
            }
        }
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    @SuppressWarnings("MultipleStringLiterals")
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
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
        ArrayList<NodeBuilder> tmpList = new ArrayList<NodeBuilder>();
        for (int i = 0; i < this.currentNodeBuilderPath.size(); i++) {
            tmpList.add(this.currentNodeBuilderPath.get(i));
        }
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        ArrayList<NodeBuilder> tmpList = new ArrayList<NodeBuilder>();
        for (int i = 0; i < this.currentNodeBuilderPath.size(); i++) {
            tmpList.add(this.currentNodeBuilderPath.get(i));
        }
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new AnswerCompletionStatusEditor(tmpList, this.currentResourceResolver);
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
     * Gets the questionnaire section node referenced by the AnswerSection
     *     NodeBuilder nb.
     *
     * @return the section Node object referenced by NodeBuilder nb
     */
    private Node getSectionNode(NodeBuilder nb)
    {
        try {
            if (nb.hasProperty("section")) {
                Session resourceSession = this.currentResourceResolver.adaptTo(Session.class);
                String sectionNodeReference = nb.getProperty("section").getValue(Type.REFERENCE);
                Node sectionNode = resourceSession.getNodeByIdentifier(sectionNodeReference);
                return sectionNode;
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
        /*
         * i == 0 --> jcr:root
         * i == 1 --> jcr:root/Forms
         * i == 2 --> jcr:root/Forms/<some form object>
         */
        LOGGER.warn("Running summarizeBuilders() on size: {}", nodeBuilders.size());
        for (int i = nodeBuilders.size() - 2; i >= 2; i--) {
            LOGGER.warn("Running summarizeBuilder() for {}", nodeBuilders.get(i));
            summarizeBuilder(nodeBuilders.get(i), nodeBuilders.get(i - 1));
        }
    }

    /**
     * Returns the first NodeBuilder with a question value equal to the
     * String uuid that is a child of the NodeBuilder nb. If no such
     * child can be found, null is returned
     *
     * @param nb the NodeBuilder to search through its children
     * @param uuid the UUID String for which the child's question
     *     property must be equal to
     * @return the first NodeBuilder with a question value equal to the
     *     String uuid that is a child of the NodeBuilder nb, or null if
     *     such node does not exist.
     */
    private NodeBuilder getChildNodeWithQuestion(NodeBuilder nb, String uuid)
    {
        Iterable<String> childrenNames = nb.getChildNodeNames();
        Iterator<String> childrenNamesIter = childrenNames.iterator();
        while (childrenNamesIter.hasNext()) {
            String selectedChildName = childrenNamesIter.next();
            NodeBuilder selectedChild = nb.getChildNode(selectedChildName);
            if (selectedChild.hasProperty("question")) {
                if (uuid.equals(selectedChild.getProperty("question").getValue(Type.STRING))) {
                    LOGGER.warn("....found the match!!");
                    return selectedChild;
                }
            }
        }
        return null;
    }

    /**
     * Evaluates the boolean expression {propA} {operator} {propB}.
     */
    private boolean evalSectionCondition(PropertyState propA, Property propB, String operator)
        throws RepositoryException, ValueFormatException
    {
        if ("=".equals(operator)) {
            LOGGER.warn("propA is of type {}", propA.getType().toString());
            if (Type.BOOLEAN.equals(propA.getType())) {
                LOGGER.warn("COMPARING BOOLEAN TYPES...");
                if (propA.getValue(Type.BOOLEAN) == propB.getValues()[0].getBoolean()) {
                    return true;
                }
            }
        }
        //If we can't evaluate it, assume it to be false
        return false;
    }

    private boolean evaluateConditionNodeRecursive(Node conditionNode, Node sectionNode, NodeBuilder prevNb) {
        if ("lfs:ConditionalGroup".equals(conditionNode.getProperty("jcr:primaryType").getString())) {
            //Evaluate recursively
            Iterator<Node> conditionChildren = conditionNode.getNodes();
            while (conditionChildren.hasNext()) {
                evaluateConditionNodeRecursive(conditionChildren.next(), sectionNode, NodeBuilder);
            }
        } else if ("lfs:Conditional".equals(conditionNode.getProperty("jcr:primaryType").getString())) {
            Node operandB = conditionNode.getNode("operandB");
            Node operandA = conditionNode.getNode("operandA");
            //TODO: Sanitize?
            String keyA = operandA.getProperty("value").getValues()[0].getString();
            LOGGER.warn("Value of keyA is {}", keyA);
            //Get the node from the Questionnaire corresponding to keyA
            Node sectionNodeParent = sectionNode.getParent();
            Node keyANode = sectionNodeParent.getNode(keyA);
            String keyANodeUUID = keyANode.getIdentifier();
            LOGGER.warn("Checking for a match to {}...", keyANodeUUID);
            //Get the node from the Form containg the answer to keyANode
            NodeBuilder conditionalFormNode = getChildNodeWithQuestion(prevNb, keyANodeUUID);
            PropertyState comparedProp = conditionalFormNode.getProperty("value");
            Property referenceProp = operandB.getProperty("value");
            return evalSectionCondition(comparedProp, referenceProp, comparator);
        }
    }

    @SuppressWarnings("checkstyle:NestedIfDepth")
    private boolean getSectionCondition(NodeBuilder nb, NodeBuilder prevNb) throws RepositoryException
    {
        Node sectionNode = getSectionNode(nb);
        if (sectionNode.hasNode("condition")) {
            Node conditionNode = sectionNode.getNode("condition");
            LOGGER.warn("...The condition node is {}", conditionNode.getIdentifier());
            /*
             * Recursively go through all children of the condition node
             * and determine if this condition node evaluates to True or
             * False.
             */
            //TODO
            //evaluateConditionNodeRecursive(conditionNode);
            String comparator = conditionNode.getProperty("comparator").getString();
            LOGGER.warn("....The comparator operator is {}", comparator);
            Iterator<Node> conditionChildren = conditionNode.getNodes();
            while (conditionChildren.hasNext()) {
                LOGGER.warn("---> has child: {}", conditionChildren.next().getIdentifier());
            }
            if (conditionNode.hasNode("operandB") && conditionNode.hasNode("operandA")) {
                Node operandB = conditionNode.getNode("operandB");
                Node operandA = conditionNode.getNode("operandA");
                //TODO: Sanitize?
                String keyA = operandA.getProperty("value").getValues()[0].getString();
                LOGGER.warn("Value of keyA is {}", keyA);
                //Get the node from the Questionnaire corresponding to keyA
                Node sectionNodeParent = sectionNode.getParent();
                Node keyANode = sectionNodeParent.getNode(keyA);
                String keyANodeUUID = keyANode.getIdentifier();
                LOGGER.warn("Checking for a match to {}...", keyANodeUUID);
                //Get the node from the Form containg the answer to keyANode
                NodeBuilder conditionalFormNode = getChildNodeWithQuestion(prevNb, keyANodeUUID);
                PropertyState comparedProp = conditionalFormNode.getProperty("value");
                Property referenceProp = operandB.getProperty("value");
                if (evalSectionCondition(comparedProp, referenceProp, comparator)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void summarizeBuilder(NodeBuilder selectedNodeBuilder, NodeBuilder prevNb) throws RepositoryException
    {
        //Iterate through all children of this node
        Iterable<String> childrenNames = selectedNodeBuilder.getChildNodeNames();
        Iterator<String> childrenNamesIter = childrenNames.iterator();
        boolean isInvalid = false;
        boolean isIncomplete = false;
        while (childrenNamesIter.hasNext()) {
            String selectedChildName = childrenNamesIter.next();
            LOGGER.warn("Checking child ----> {}", selectedChildName);
            NodeBuilder selectedChild = selectedNodeBuilder.getChildNode(selectedChildName);
            if ("lfs:AnswerSection".equals(selectedChild.getProperty("jcr:primaryType").getValue(Type.STRING))) {
                LOGGER.warn("........ {} is an AnswerSection, we may not need to include it.", selectedChildName);
                if (getSectionCondition(selectedChild, selectedNodeBuilder)) {
                    LOGGER.warn(" **** INCLUDE IT! ****");
                } else {
                    LOGGER.warn(" **** SKIP IT! ****");
                    continue;
                }
            }
            //Is selectedChild - invalid? , incomplete?
            if (selectedChild.hasProperty(STATUS_FLAGS)) {
                Iterable<String> selectedProps = selectedChild.getProperty(STATUS_FLAGS).getValue(Type.STRINGS);
                Iterator<String> selectedPropsIter = selectedProps.iterator();
                while (selectedPropsIter.hasNext()) {
                    String thisStr = selectedPropsIter.next();
                    if (STATUS_FLAG_INVALID.equals(thisStr)) {
                        isInvalid = true;
                    }
                    if (STATUS_FLAG_INCOMPLETE.equals(thisStr)) {
                        isIncomplete = true;
                    }
                }
            }
        }
        //Set the flags in selectedNodeBuilder accordingly
        ArrayList<String> statusFlags = new ArrayList<String>();
        if (isInvalid) {
            statusFlags.add(STATUS_FLAG_INVALID);
        }
        if (isIncomplete) {
            statusFlags.add(STATUS_FLAG_INCOMPLETE);
        }
        //Write these statusFlags to the JCR repo
        selectedNodeBuilder.setProperty(STATUS_FLAGS, statusFlags, Type.STRINGS);
    }
}
