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

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Session;

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
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * An {@link Editor} that creates any missing answers and answer sections, following the questionnaire as a template.
 *
 * @version $Id$
 */
public class CreateMissingAnswersEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateMissingAnswersEditor.class);

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    private final ThreadResourceResolverProvider rrp;

    /** The current user session. */
    private final Session currentSession;

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private final boolean isFormNode;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param currentSession the current user session
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param rrp for sharing the resource resolver with other components
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     */
    public CreateMissingAnswersEditor(final NodeBuilder nodeBuilder, final Session currentSession,
        final ResourceResolverFactory rrf, final ThreadResourceResolverProvider rrp,
        final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.currentSession = currentSession;
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
            return new CreateMissingAnswersEditor(this.currentNodeBuilder.getChildNode(name), this.currentSession,
                this.rrf, this.rrp, this.questionnaireUtils, this.formUtils);
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
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "createMissingAnswers"))) {
            this.rrp.push(serviceResolver);
            mustPopResolver = true;
            createMissingNodes();
        } catch (final LoginException e) {
            // Should not happen
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void createMissingNodes()
    {
        final Node questionnaireNode = this.formUtils.getQuestionnaire(this.currentNodeBuilder);
        if (questionnaireNode == null) {
            return;
        }

        final FormGenerator generator = new FormGenerator(this.questionnaireUtils, this.formUtils,
            this.currentSession);
        generator.createMissingNodes(questionnaireNode, this.currentNodeBuilder);
    }
}
