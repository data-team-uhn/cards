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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import io.uhndata.cards.forms.api.FormUtils;

/**
 * An {@link Editor} that sets the {@code form} property for every Answer. Normally this wouldn't be needed, but queries
 * joining answers to their forms using {@code isdescendantnode} don't scale well.
 *
 * @version $Id$
 */
public class AnswerFormEditor extends DefaultEditor
{
    private final NodeBuilder currentNodeBuilder;

    private final FormUtils formUtils;

    private final boolean isFormNode;

    private final String formIdentifier;

    /**
     * Simple constructor.
     *
     * @param currentNodeBuilder the NodeBuilder to process
     * @param formUtils for working with form data
     */
    public AnswerFormEditor(final NodeBuilder currentNodeBuilder, final FormUtils formUtils)
    {
        this.currentNodeBuilder = currentNodeBuilder;
        this.formUtils = formUtils;
        this.isFormNode = this.formUtils.isForm(currentNodeBuilder);
        this.formIdentifier = this.isFormNode ? currentNodeBuilder.getProperty("jcr:uuid").getValue(Type.STRING) : null;
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
        throws CommitFailedException
    {
        if (this.isFormNode) {
            return null;
        }
        return new AnswerFormEditor(this.currentNodeBuilder.getChildNode(name), this.formUtils);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        if (this.isFormNode) {
            processNode(this.currentNodeBuilder);
        }
    }

    private void processNode(final NodeBuilder node)
    {
        node.getChildNodeNames().iterator().forEachRemaining(name -> processNode(node.getChildNode(name)));
        if (!this.formUtils.isForm(node)) {
            node.setProperty("form", this.formIdentifier);
        }
    }
}
