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

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUpdateUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * A {@link EditorProvider} returning {@link IdentifierAnswerEditor}.
 *
 * @version $Id$
 */
@Component(property = "service.ranking:Integer=10")
public class IdentifierAnswerEditorProvider implements EditorProvider
{
    @Reference
    private FormUtils formUtils;

    @Reference
    private FormUpdateUtils formUpdateUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public Editor getRootEditor(final NodeState before, final NodeState after, final NodeBuilder builder,
        final CommitInfo info) throws CommitFailedException
    {
        final ResourceResolver resolver = this.rrp.getThreadResourceResolver();
        if (resolver != null) {
            // Each QuestionMatrixEditor maintains a state, so a new instance must be returned each time
            return new IdentifierAnswerEditor(builder, resolver.adaptTo(Session.class),
                this.formUtils, this.formUpdateUtils, this.questionnaireUtils, false);
        }
        return null;
    }
}
