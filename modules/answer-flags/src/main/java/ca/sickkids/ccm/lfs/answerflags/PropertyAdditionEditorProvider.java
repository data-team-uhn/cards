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
package ca.sickkids.ccm.lfs.answerflags;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link EditorProvider} returning {@link PropertyAdditionEditor}.
 *
 * @version $Id$
 */
@Component(name = "PropertyAdditionEditorProvider", service = EditorProvider.class, scope = ServiceScope.SINGLETON,
    immediate = true)
public class PropertyAdditionEditorProvider implements EditorProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAdditionEditor.class);

    @Reference(fieldOption = FieldOption.REPLACE,
        cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Override
    public Editor getRootEditor(NodeState before, NodeState after, NodeBuilder builder, CommitInfo info)
        throws CommitFailedException
    {
        if (this.rrf != null) {
            ResourceResolver myResolver = this.rrf.getThreadResourceResolver();
            if (myResolver != null) {
                LOGGER.warn("Got the ResourceResolver...");
                // Each PropertyAdditionEditor maintains a state, so a new instance must be returned each time
                return new PropertyAdditionEditor(builder, myResolver);
            }
        }
        return null;
    }
}
