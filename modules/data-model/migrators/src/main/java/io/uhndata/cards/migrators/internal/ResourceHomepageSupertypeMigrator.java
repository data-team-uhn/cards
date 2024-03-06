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
package io.uhndata.cards.migrators.internal;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.migrators.spi.DataMigrator;

/**
 * Migrator that updates the {@code sling:resourceSuperType} property of resource homepage nodes to the required type,
 * {@code cards/ResourceHomepage}. In the earlier versions, we didn't have {@code ResourceHomepage} as a resource type,
 * so nodes were created with a protected supertype value of {@code cards/Resource}. Since protected properties cannot
 * be updated using the usual JCR APIs, we must use the internal Oak commit editor mechanism to update them. This
 * migrator only runs if the required supertype is not set, and it works by temporarily registering a commit editor,
 * triggering a save, then unregistering the commit editor.
 *
 * @version $Id$
 * @since 0.9.23
 */
@Component(immediate = true)
public class ResourceHomepageSupertypeMigrator implements DataMigrator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceHomepageSupertypeMigrator.class);

    private BundleContext bundleContext;

    @Activate
    protected void activate(ComponentContext context)
    {
        this.bundleContext = context.getBundleContext();
    }

    @Override
    public String getName()
    {
        return "ResourceHomepageSupertypeMigrator";
    }

    @Override
    public int getPriority()
    {
        // Doesn't matter that much
        return 0;
    }

    @Override
    public boolean shouldRun(Version previousVersion, Version currentVersion, Session session)
    {
        try {
            return !session.propertyExists("/Forms/sling:resourceSuperType")
                || !"cards/ResourceHomepage".equals(session.getProperty("/Forms/sling:resourceSuperType").getString());
        } catch (RepositoryException e) {
            return false;
        }
    }

    @Override
    public void run(Version previousVersion, Version currentVersion, Session session)
    {
        ServiceRegistration<EditorProvider> service =
            this.bundleContext.registerService(EditorProvider.class, new SupertypeFixerProvider(), null);
        try {
            Node node = session.getNode("/Forms");
            Property tempProperty = node.setProperty("temp", "temp");
            session.save();
            tempProperty.remove();
            session.save();
        } catch (RepositoryException e) {
            // Should not happen
            LOGGER.error("Failed to update the supertype of homepages", e);
        } finally {
            service.unregister();
        }
    }

    class SupertypeFixerProvider implements EditorProvider
    {
        private final AtomicBoolean used = new AtomicBoolean(false);

        @Override
        public Editor getRootEditor(NodeState before, NodeState after, NodeBuilder builder, CommitInfo info)
            throws CommitFailedException
        {
            if (!this.used.getAndSet(true)) {
                return new SupertypeFixer(builder);
            }
            return null;
        }
    }

    class SupertypeFixer extends DefaultEditor implements Editor
    {
        private final NodeBuilder builder;

        SupertypeFixer(final NodeBuilder builder)
        {
            this.builder = builder;
        }

        @Override
        public void enter(NodeState before, NodeState after) throws CommitFailedException
        {
            for (String s : new String[] { "Forms", "Subjects", "Questionnaires", "SubjectTypes" }) {
                fixNode(this.builder.getChildNode(s));
            }
        }

        private void fixNode(final NodeBuilder node)
        {
            if (!node.exists()) {
                return;
            }
            node.setProperty("sling:resourceSuperType", "cards/ResourceHomepage", Type.STRING);
        }
    }
}
