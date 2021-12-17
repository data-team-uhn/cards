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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * A {@link EditorProvider} returning {@link QuestionMatrixEditor}.
 *
 * @version $Id$
 */
@Component(service = EditorProvider.class, scope = ServiceScope.SINGLETON, immediate = true)
public class QuestionMatrixEditorProvider implements EditorProvider
{
    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)

    private ResourceResolverFactory rrf;

    @Override
    public Editor getRootEditor(final NodeState before, final NodeState after, final NodeBuilder builder,
        final CommitInfo info)
        throws CommitFailedException
    {
        if (this.rrf != null) {
            final ResourceResolver myResolver = this.rrf.getThreadResourceResolver();
            if (myResolver != null) {
                Session session = myResolver.adaptTo(Session.class);
                // Each FormRelatedSubjectsEditor maintains a state, so a new instance must be returned each time
                return new QuestionMatrixEditor(builder, session);
            }
        }
        return null;
    }
}
