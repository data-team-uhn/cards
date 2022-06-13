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

import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

// A Validator that check if number of created forms of specific type didn't reach the maximum value allowed for subject
public class MaxFormsOfTypePerSubjectValidator extends DefaultValidator
{
    private final ResourceResolver resolver;
    private final Session session;

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    /**
     * Simple constructor.
     *
     * @param resolver the resource resolver factory which can provide access to database
     * @param session the current JCR session
     * @param currentNodeBuilder the current node
     */
    public MaxFormsOfTypePerSubjectValidator(ResourceResolver resolver, Session session, NodeBuilder currentNodeBuilder)
    {
        this.resolver = resolver;
        this.session = session;
        this.currentNodeBuilder = currentNodeBuilder;
    }


    // When something changes in a node deep in the content tree, the validator is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultValidator is to stop at the root, so we must override the following two methods in order for the validator
    // to be invoked on non-root nodes.
    @Override
    public Validator childNodeAdded(String name, NodeState after) throws CommitFailedException
    {

        if (isForm(this.currentNodeBuilder)) {
            // If this is a form, we need to get its questionnaire and related subject to count number of these forms
            try {
                Node questionnaire = this.session.getNodeByIdentifier(this.currentNodeBuilder
                        .getProperty("questionnaire").getValue(Type.REFERENCE));
                Node subject = this.session.getNodeByIdentifier(this.currentNodeBuilder.getProperty("subject")
                        .getValue(Type.REFERENCE));
                long maxFormNumberPerSubject = questionnaire.getProperty("maxPerSubject").getLong();
                if (maxFormNumberPerSubject > 0) {
                    long formNumber = countFormsPerSubject(subject.getProperty("uuid").getString(),
                            questionnaire.getProperty("title").getString()) + 1;
                    if (formNumber > maxFormNumberPerSubject) {
                        throw new CommitFailedException(CommitFailedException.STATE, 400,
                                "The number of created forms is bigger then is allowed");
                    }
                }
                // If this is already a form, there's no need to descend further down, there aren't any sub-forms
                return null;
            } catch (RepositoryException e) {
                throw new RuntimeException(e);
            }
        }
        return new MaxFormsOfTypePerSubjectValidator(this.resolver, this.session,
                this.currentNodeBuilder.getChildNode(name));
    }

    @Override
    public Validator childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return childNodeAdded(name, after);
    }

    /**
     * Counts the number of created forms per subject with specific questionnaire title.
     *
     * @param subjectUUID subject to be checked id
     * @param excludeFormQuestionnaireType type of questionnaire to be count per subject
     * @return a long
     */
    private long countFormsPerSubject(String subjectUUID, String excludeFormQuestionnaireType)
    {
        long count = 0;
        Iterator<Resource> results = this.resolver.findResources(
                "SELECT * FROM [cards:Form] as f INNER JOIN [cards:Questionnaires] as q"
                        + " ON f.'questionnaire'=q.'jcr:uuid' as fq WHERE fq.'subject'='" + subjectUUID + "'"
                        + " AND fq.'title'<>'" + excludeFormQuestionnaireType + "'",
                "JCR-SQL2"
        );
        while (results.hasNext()) {
            count += 1;
            results.next();
        }
        return count;
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
