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
package ca.sickkids.ccm.lfs.sling.jcr.oak.server.it;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.BundleContext;

import static org.apache.sling.testing.paxexam.SlingOptions.scr;
import static org.apache.sling.testing.paxexam.SlingOptions.slingJcr;
import static org.apache.sling.testing.paxexam.SlingOptions.slingJcrRepoinit;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public abstract class OakServerTestSupport extends TestSupport
{

    protected static final Integer TEST_SCALE = Integer.getInteger("test.scale", 1);

    @Inject
    protected SlingRepository slingRepository;

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected SlingRepository repository;

    @Inject
    protected ResourceResolverFactory resourceResolverFactory;

    protected final List<String> toDelete = new LinkedList<>();

    private final AtomicInteger uniqueNameCounter = new AtomicInteger();

    protected class JcrEventsCounter implements EventListener
    {
        private final Session s;

        private int jcrEventsCounter;

        public JcrEventsCounter() throws RepositoryException
        {
            this.s = OakServerTestSupport.this.repository.loginAdministrative(null);
            final ObservationManager om = this.s.getWorkspace().getObservationManager();
            // not sure if that's a recommended value, but common
            final int eventTypes = 255;
            final boolean deep = true;
            final String[] uuid = null;
            final String[] nodeTypeNames = new String[] { "mix:language", "sling:Message" };
            final boolean noLocal = true;
            final String root = "/";
            om.addEventListener(this, eventTypes, root, deep, uuid, nodeTypeNames, noLocal);
        }

        void close()
        {
            this.s.logout();
        }

        @Override
        public void onEvent(EventIterator it)
        {
            while (it.hasNext()) {
                it.nextEvent();
                this.jcrEventsCounter++;
            }
        }

        int get()
        {
            return this.jcrEventsCounter;
        }
    }

    protected Node deleteAfterTests(Node it) throws RepositoryException
    {
        this.toDelete.add(it.getPath());
        return it;
    }

    /**
     * Verify that admin can create and retrieve a node of the specified type.
     *
     * @return the path of the test node that was created.
     */
    protected String assertCreateRetrieveNode(String nodeType) throws RepositoryException
    {
        return assertCreateRetrieveNode(nodeType, null);
    }

    protected String assertCreateRetrieveNode(String nodeType, String relParentPath) throws RepositoryException
    {
        Session session = this.repository.loginAdministrative(null);
        try {
            final Node root = session.getRootNode();
            final String name = uniqueName("assertCreateRetrieveNode");
            final String propName = "PN_" + name;
            final String propValue = "PV_" + name;
            final Node parent = relParentPath == null ? root : JcrUtils.getOrAddNode(root, relParentPath);
            final Node child = nodeType == null ? parent.addNode(name) : parent.addNode(name, nodeType);
            child.setProperty(propName, propValue);
            child.setProperty("foo", child.getPath());
            session.save();
            session.logout();
            session = this.repository.loginAdministrative(null);
            final String path = relParentPath == null ? "/" + name : "/" + relParentPath + "/" + name;
            final Node n = session.getNode(path);
            assertNotNull(n);
            assertEquals(propValue, n.getProperty(propName).getString());
            return n.getPath();
        } finally {
            session.logout();
        }
    }

    protected String uniqueName(String hint)
    {
        return hint + "_" + this.uniqueNameCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }

    @Configuration
    public Option[] configuration()
    {
        return new Option[] {
        baseConfiguration(),
        launchpad(),
        // Sling JCR Oak Server
        testBundle("bundle.filename"),
        // testing
        junitBundles()
        };
    }

    protected Option launchpad()
    {
        final String repoinit = String.format("raw:file:%s/src/test/resources/repoinit.txt", PathUtils.getBaseDir());
        final String slingHome = String.format("%s/sling", workingDirectory());
        final String repositoryHome = String.format("%s/repository", slingHome);
        final String localIndexDir = String.format("%s/index", repositoryHome);
        return composite(
            scr(),
            slingJcr(),
            slingJcrRepoinit(),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-segment-tar").version(versionResolver),
            newConfiguration("org.apache.jackrabbit.oak.segment.SegmentNodeStoreService")
                .put("repository.home", repositoryHome)
                .put("name", "Default NodeStore")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProviderService")
                .put("localIndexDir", localIndexDir)
                .asOption(),
            newConfiguration("org.apache.sling.jcr.repoinit.impl.RepositoryInitializer")
                .put("references", new String[] { repoinit })
                .asOption(),
            getWhitelistRegexpOption(),
            // To generate the list of whitelisted bundles after a failed test-run:
            // grep -R 'NOT white' target/failsafe-reports/ | awk -F': Bundle ' '{print substr($2, 1, index($2, " is NOT
            // "))}' | sort -u
            factoryConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment")
                .put("whitelist.bundles", new String[] {
                    "org.apache.sling.jcr.oak.server",
                    "ca.sickkids.ccm.lfs.custom.jcr.oak.server",
                    "org.apache.sling.jcr.contentloader",
                    "org.apache.sling.jcr.resource",
                    "org.apache.sling.resourceresolver"
                })
                .asOption());
    }

    protected Option getWhitelistRegexpOption()
    {
        return newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
            .put("whitelist.bundles.regexp", "PAXEXAM-PROBE-.*")
            .asOption();
    }
}
