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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * An {@link Editor} that sorts answers and answer sections in a form, following the questionnaire as a template.
 *
 * @version $Id$
 */
public class SortChildrenEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SortChildrenEditor.class);

    private static final String ORDER_PROPERTY = ":childOrder";

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    private final ThreadResourceResolverProvider rrp;

    private final FormUtils formUtils;

    private final boolean isFormNode;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param rrp for sharing the resource resolver with other components
     * @param formUtils for working with form data
     */
    public SortChildrenEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final ThreadResourceResolverProvider rrp, final FormUtils formUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.formUtils = formUtils;
        this.rrf = rrf;
        this.rrp = rrp;
        this.isFormNode = this.formUtils.isForm(nodeBuilder);
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return null;
        } else {
            return new SortChildrenEditor(this.currentNodeBuilder.getChildNode(name), this.rrf, this.rrp,
                this.formUtils);
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        if (!this.isFormNode) {
            return;
        }

        boolean mustPopResolver = false;
        try (ResourceResolver serviceResolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "sortChildren"))) {
            this.rrp.push(serviceResolver);
            mustPopResolver = true;
            sortNodes();
        } catch (final LoginException e) {
            // Should not happen
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void sortNodes()
    {
        final Node questionnaireNode = this.formUtils.getQuestionnaire(this.currentNodeBuilder);
        if (questionnaireNode == null) {
            return;
        }
        sortNodes(this.currentNodeBuilder, questionnaireNode);
    }

    private void sortNodes(final NodeBuilder node, final Node definition)
    {
        final List<Pair<String, NodeBuilder>> sortedNodes = new ArrayList<>();
        // First we maintain the original order, since this will keep the order in which nodes were added
        if (node.hasProperty(ORDER_PROPERTY)) {
            PropertyState initialOrder = node.getProperty(ORDER_PROPERTY);
            for (int i = 0; i < initialOrder.count(); ++i) {
                final String childName = initialOrder.getValue(Type.STRING, i);
                final NodeBuilder child = node.getChildNode(childName);
                if (child.exists()) {
                    sortedNodes.add(Pair.of(childName, child));
                }
            }
        }
        // We add any nodes not found in the original :childOrder
        for (String childName : node.getChildNodeNames()) {
            if (sortedNodes.stream().anyMatch(p -> childName.equals(p.getKey()))) {
                continue;
            }
            NodeBuilder child = node.getChildNode(childName);
            sortedNodes.add(Pair.of(childName, child));
            if (this.formUtils.isAnswerSection(child)) {
                sortNodes(child, this.formUtils.getSection(child));
            }
        }
        // We sort by the order imposed by the questionnaire
        // Since this is a stable sort, it will maintain the relative ordering of repeatable sections
        sortedNodes.sort(new DefinitionComparator(definition));
        node.setProperty(ORDER_PROPERTY, sortedNodes.stream().map(Pair::getKey).collect(Collectors.toList()),
            Type.NAMES);
    }

    class DefinitionComparator implements Comparator<Pair<String, NodeBuilder>>
    {
        private final List<String> definitionUuids = new ArrayList<>();

        DefinitionComparator(final Node definition)
        {
            NodeIterator children;
            try {
                children = definition.getNodes();
                while (children.hasNext()) {
                    Node child = children.nextNode();
                    if (child.hasProperty("jcr:uuid")) {
                        this.definitionUuids.add(child.getIdentifier());
                    }
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Unexpected exception while sorting children: {}", e.getMessage(), e);
            }
        }

        @Override
        public int compare(Pair<String, NodeBuilder> child1, Pair<String, NodeBuilder> child2)
        {
            final String uuid1 = this.getDefinitionUuid(child1.getValue());
            final String uuid2 = this.getDefinitionUuid(child2.getValue());

            if (this.definitionUuids.contains(uuid1) && this.definitionUuids.contains(uuid2)) {
                return this.definitionUuids.indexOf(uuid1) - this.definitionUuids.indexOf(uuid2);
            } else {
                return 0;
            }
        }

        private String getDefinitionUuid(final NodeBuilder node)
        {
            if (SortChildrenEditor.this.formUtils.isAnswer(node)) {
                return SortChildrenEditor.this.formUtils.getQuestionIdentifier(node);
            } else if (SortChildrenEditor.this.formUtils.isAnswerSection(node)) {
                return SortChildrenEditor.this.formUtils.getSectionIdentifier(node);
            }
            return "";
        }
    }
}
