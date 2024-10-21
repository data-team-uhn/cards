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

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUpdateUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;

/**
 * Basic utilities for working with Form data.
 *
 * @version $Id$
 */
@Component
public final class FormUpdateUtilsImpl extends AbstractNodeUtils implements FormUpdateUtils
{
    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    public NodeBuilder getOrGenerateChild(final NodeBuilder formParent, final Node questionnaireNode)
    {
        boolean isQuestion = this.questionnaireUtils.isQuestion(questionnaireNode);
        boolean isSection = this.questionnaireUtils.isSection(questionnaireNode);

        NodeBuilder result = null;
        if (isQuestion || isSection) {
            try {
                String questionnaireNodeIdentifier = questionnaireNode.getIdentifier();
                for (ChildNodeEntry entry : formParent.getNodeState().getChildNodeEntries()) {
                    NodeState childNode = entry.getNodeState();
                    String id = isQuestion
                        ? this.formUtils.getQuestionIdentifier(childNode)
                        : this.formUtils.getSectionIdentifier(childNode);
                    if (questionnaireNodeIdentifier.equals(id)) {
                        result = formParent.getChildNode(entry.getName());
                        break;
                    }
                }

                if (result == null) {
                    result = isQuestion
                        ? generateQuestion(formParent, questionnaireNode)
                        : generateSection(formParent, questionnaireNode);
                }
            } catch (RepositoryException e) {
                // Unable to retrieve node identifier.
                // Do nothing, return default
            }
        }
        return result;
    }

    public NodeBuilder getOrGeneratePath(final NodeBuilder formNode, final String path, final Node questionnaireNode)
    {
        NodeBuilder currentNode = formNode;
        String[] pathSegments = path.split("/");
        for (String subPath : pathSegments) {
            try {
                currentNode = getOrGenerateChild(formNode, questionnaireNode.getNode(subPath));
            } catch (RepositoryException e) {
                currentNode = null;
            }
            if (currentNode == null) {
                break;
            }
        }
        return currentNode;
    }

    protected NodeBuilder generateQuestion(final NodeBuilder parent, final Node question)
    {
        try {
            String dataType = StringUtils.capitalize(question.getProperty("dataType").getString());
            String nodeType = "cards:" + dataType + "Answer";
            String resourceType = "cards/" + dataType + "Answer";
            return generateNode(parent, FormUtils.QUESTION_PROPERTY, question.getIdentifier(),
                nodeType, FormUtils.ANSWER_RESOURCE, resourceType);
        } catch (RepositoryException e) {
            // Failed to retrieve either question identifier or question data type
            return null;
        }

    }

    protected NodeBuilder generateSection(final NodeBuilder parent, final Node section)
    {
        try {
            return generateNode(parent, FormUtils.SECTION_PROPERTY, section.getIdentifier(),
                FormUtils.ANSWER_SECTION_NODETYPE, FormUtils.ANSWER_SECTION_SUPERTYPE,
                FormUtils.ANSWER_SECTION_RESOURCE);
        } catch (RepositoryException e) {
            // Failed to retrieve the section identifier
            return null;
        }
    }

    protected NodeBuilder generateNode(final NodeBuilder parent, final String questionnaireNodeProperty,
        final String questionnaireNodeID, final String nodeType, final String superType, final String resourceType)
    {
        final String uuid = UUID.randomUUID().toString();
        NodeBuilder node = parent.setChildNode(uuid);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        node.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
        node.setProperty("jcr:createdBy", this.rrp.getThreadResourceResolver().getUserID(), Type.NAME);
        node.setProperty(questionnaireNodeProperty, questionnaireNodeID, Type.REFERENCE);
        node.setProperty("jcr:primaryType", nodeType, Type.NAME);
        node.setProperty("sling:resourceSuperType", superType, Type.STRING);
        node.setProperty("sling:resourceType", resourceType, Type.STRING);
        node.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);

        return node;
    }
}
