/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ca.sickkids.ccm.lfs.sling.jcr.oak.server.internal;

import java.util.Collections;
import java.util.Dictionary;

import javax.jcr.Repository;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.oak.InitialContent;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.osgi.OsgiWhiteboard;
import org.apache.jackrabbit.oak.plugins.commit.ConflictValidatorProvider;
import org.apache.jackrabbit.oak.plugins.commit.JcrConflictHandler;
import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.plugins.index.IndexUtils;
import org.apache.jackrabbit.oak.plugins.index.WhiteboardIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.aggregate.SimpleNodeAggregator;
import org.apache.jackrabbit.oak.plugins.index.lucene.util.LuceneIndexHelper;
import org.apache.jackrabbit.oak.plugins.name.NameValidatorProvider;
import org.apache.jackrabbit.oak.plugins.name.NamespaceEditorProvider;
import org.apache.jackrabbit.oak.plugins.nodetype.TypeEditorProvider;
import org.apache.jackrabbit.oak.plugins.observation.CommitRateLimiter;
import org.apache.jackrabbit.oak.plugins.version.VersionHook;
import org.apache.jackrabbit.oak.spi.commit.WhiteboardEditorProvider;
import org.apache.jackrabbit.oak.spi.lifecycle.RepositoryInitializer;
import org.apache.jackrabbit.oak.spi.query.QueryIndex.NodeAggregator;
import org.apache.jackrabbit.oak.spi.query.WhiteboardIndexProvider;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;
import org.apache.sling.jcr.base.AbstractSlingRepository2;
import org.apache.sling.jcr.base.AbstractSlingRepositoryManager;
import org.apache.sling.serviceusermapping.ServiceUserMapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import com.google.common.collect.ImmutableSet;

/**
 * A Sling repository implementation that wraps the Oak repository implementation from the Jackrabbit Oak project.
 *
 * @version $Id$
 */
@Component(
    property = {
    Constants.SERVICE_DESCRIPTION + "=Apache Sling JCR Oak Repository Manager",
    Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
    })
@Designate(
    ocd = OakSlingRepositoryManagerConfiguration.class)
@SuppressWarnings({ "checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling" })
public class OakSlingRepositoryManager extends AbstractSlingRepositoryManager
{
    @Reference
    private ServiceUserMapper serviceUserMapper;

    @Reference
    private NodeStore nodeStore;

    private ComponentContext componentContext;

    private final WhiteboardIndexProvider indexProvider = new WhiteboardIndexProvider();

    private final WhiteboardIndexEditorProvider indexEditorProvider = new WhiteboardIndexEditorProvider();

    private CommitRateLimiter commitRateLimiter;

    private OakSlingRepositoryManagerConfiguration configuration;

    @Reference(
        policy = ReferencePolicy.STATIC,
        policyOption = ReferencePolicyOption.GREEDY)
    private SecurityProvider securityProvider;

    private ServiceRegistration nodeAggregatorRegistration;

    private WhiteboardCommitHook commitHook;

    private WhiteboardEditorProvider editorProvider;

    private WhiteboardRepositoryInitializer repositoryInitializer;

    @Override
    protected ServiceUserMapper getServiceUserMapper()
    {
        return this.serviceUserMapper;
    }

    @Override
    protected Repository acquireRepository()
    {
        final BundleContext bundleContext = this.componentContext.getBundleContext();
        final Whiteboard whiteboard = new OsgiWhiteboard(bundleContext);
        this.indexProvider.start(whiteboard);
        this.indexEditorProvider.start(whiteboard);

        final Oak oak = new Oak(this.nodeStore)
            .withAsyncIndexing("async", 5);

        final Jcr jcr = new Jcr(oak, false)
            // Initial content
            .with(new InitialContent())
            .with(new ExtraSlingContent())
            // Security configuration
            .with(this.securityProvider)
            // Index configuration
            .with(this.indexProvider)
            .with(this.indexEditorProvider)
            // Conflict handler, currently a hard-coded list of conflict handlers; should this be dynamic?
            .with(JcrConflictHandler.createJcrConflictHandler())
            // Other settings
            .with(getDefaultWorkspace())
            .with(whiteboard)
            .withFastQueryResultSize(true)
            .withObservationQueueLength(this.configuration.oak_observation_queue_length());
        if (this.configuration.dynamic()) {
            // Additional RepositoryInitializers; the two initializers added above are not proper components, so they
            // will not be included in this dynamic list
            this.repositoryInitializer = new WhiteboardRepositoryInitializer();
            this.repositoryInitializer.start(whiteboard);
            jcr.with(this.repositoryInitializer);
            // CommitHooks
            this.commitHook = new WhiteboardCommitHook();
            this.commitHook.start(whiteboard);
            jcr.with(this.commitHook);
            // EditorProviders
            this.editorProvider = new WhiteboardEditorProvider();
            this.editorProvider.start(whiteboard);
            jcr.with(this.editorProvider);
        } else {
            jcr
                // CommitHooks
                .with(new VersionHook())
                // EditorProviders
                .with(new NameValidatorProvider())
                .with(new NamespaceEditorProvider())
                .with(new TypeEditorProvider())
                .with(new ConflictValidatorProvider());
        }

        if (this.commitRateLimiter != null) {
            jcr.with(this.commitRateLimiter);
        }

        jcr.createContentRepository();

        return new TcclWrappingJackrabbitRepository((JackrabbitRepository) jcr.createRepository());
    }

    @Override
    protected Dictionary<String, Object> getServiceRegistrationProperties()
    {
        return this.componentContext.getProperties();
    }

    @Override
    protected AbstractSlingRepository2 create(Bundle usingBundle)
    {
        final String adminId = getAdminId();
        return new OakSlingRepository(this, usingBundle, adminId);
    }

    @Override
    protected void destroy(AbstractSlingRepository2 repositoryServiceInstance)
    {
        // nothing to do, just drop the reference
    }

    @Override
    protected void disposeRepository(Repository repository)
    {
        this.indexProvider.stop();
        this.indexEditorProvider.stop();
        if (this.editorProvider != null) {
            this.editorProvider.stop();
        }
        if (this.commitHook != null) {
            this.commitHook.stop();
        }
        if (this.repositoryInitializer != null) {
            this.repositoryInitializer.stop();
        }
        ((JackrabbitRepository) repository).shutdown();
    }

    @Activate
    private void activate(final OakSlingRepositoryManagerConfiguration configuration,
        final ComponentContext componentContext)
    {
        this.configuration = configuration;
        this.componentContext = componentContext;
        final BundleContext bundleContext = componentContext.getBundleContext();

        final String defaultWorkspace = configuration.defaultWorkspace();
        final boolean disableLoginAdministrative = !configuration.admin_login_enabled();

        if (configuration.oak_observation_limitCommitRate()) {
            this.commitRateLimiter = new CommitRateLimiter();
        }
        this.nodeAggregatorRegistration =
            bundleContext.registerService(NodeAggregator.class.getName(), getNodeAggregator(), null);

        super.start(bundleContext, new Config(defaultWorkspace, disableLoginAdministrative));
    }

    @Deactivate
    private void deactivate()
    {
        super.stop();
        this.componentContext = null;
        this.nodeAggregatorRegistration.unregister();
    }

    private String getAdminId()
    {
        return this.securityProvider.getConfiguration(UserConfiguration.class).getParameters()
            .getConfigValue(UserConstants.PARAM_ADMIN_ID, UserConstants.DEFAULT_ADMIN_ID);
    }

    private static NodeAggregator getNodeAggregator()
    {
        return new SimpleNodeAggregator().newRuleWithName(JcrConstants.NT_FILE,
            Collections.singletonList(JcrConstants.JCR_CONTENT));
    }

    private static final class ExtraSlingContent implements RepositoryInitializer
    {

        @Override
        public void initialize(NodeBuilder root)
        {
            if (root.hasChildNode(IndexConstants.INDEX_DEFINITIONS_NAME)) {
                NodeBuilder index = root.child(IndexConstants.INDEX_DEFINITIONS_NAME);

                // jcr:
                property(index, "jcrLanguage", "jcr:language");
                property(index, "jcrLockOwner", "jcr:lockOwner");

                // sling:
                property(index, "slingAlias", "sling:alias");
                property(index, "slingResource", "sling:resource");
                property(index, "slingResourceType", "sling:resourceType");
                property(index, "slingVanityPath", "sling:vanityPath");

                // various
                property(index, "event.job.topic", "event.job.topic");
                property(index, "slingeventEventId", "slingevent:eventId");
                property(index, "extensionType", "extensionType");
                property(index, "lockCreated", "lock.created");
                property(index, "status", "status");
                property(index, "type", "type");

                // lucene full-text index
                if (!index.hasChildNode("lucene")) {
                    LuceneIndexHelper.newLuceneIndexDefinition(
                        index, "lucene", LuceneIndexHelper.JR_PROPERTY_INCLUDES,
                        ImmutableSet.of(
                            "jcr:createdBy",
                            "jcr:lastModifiedBy",
                            "sling:alias",
                            "sling:resourceType",
                            "sling:vanityPath"),
                        "async");
                }

            }
        }

        /**
         * A convenience method to create a non-unique property index.
         */
        private static void property(NodeBuilder index, String indexName, String propertyName)
        {
            if (!index.hasChildNode(indexName)) {
                IndexUtils.createIndexDefinition(index, indexName, true, false, Collections.singleton(propertyName),
                    null);
            }
        }

    }

}
