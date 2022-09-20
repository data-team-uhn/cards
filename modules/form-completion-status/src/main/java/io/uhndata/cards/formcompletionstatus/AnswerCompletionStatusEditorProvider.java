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
package io.uhndata.cards.formcompletionstatus;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

/**
 * A {@link EditorProvider} returning {@link AnswerCompletionStatusEditor}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class AnswerCompletionStatusEditorProvider implements EditorProvider
{
    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    /** A list of all available {@link AnswerValidator}s. */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<AnswerValidator> allValidators;

    @Override
    public Editor getRootEditor(final NodeState before, final NodeState after, final NodeBuilder builder,
        final CommitInfo info)
        throws CommitFailedException
    {
        ResourceResolver resolver = this.rrp.getThreadResourceResolver();
        if (resolver != null) {
            this.allValidators.sort((o1, o2) -> o2.getPriority() - o1.getPriority());
            // Each AnswerCompletionStatusEditor maintains a state, so a new instance must be returned each time
            final List<NodeBuilder> tmpList = new ArrayList<>();
            tmpList.add(builder);
            return new AnswerCompletionStatusEditor(tmpList, null, resolver.adaptTo(Session.class), this.formUtils,
                this.allValidators);
        }
        return null;
    }
}
