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

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DynamicOakServerIT extends OakServerTestSupport
{

    @Override
    @Configuration
    public Option[] configuration()
    {
        return new Option[] {
        baseConfiguration(),
        launchpad(),
        // Sling JCR Oak Server
        testBundle("bundle.filename"),
        // testing
        junitBundles(),
        newConfiguration("ca.sickkids.ccm.lfs.sling.jcr.oak.server.internal.OakSlingRepositoryManager")
            .put("dynamic", true)
            .asOption()
        };
    }

    @Test
    public void testRepositoryPresent()
    {
        assertNotNull(this.repository);
    }

    @Test
    public void testDynamicCommitHooksAndEditorsAreInvoked() throws Exception
    {
        final ServiceRegistration<CommitHook> reg =
            this.bundleContext.registerService(CommitHook.class, new TestCommitHook(), null);
        final ServiceRegistration<EditorProvider> reg2 =
            this.bundleContext.registerService(EditorProvider.class, new TestEditorProvider(), null);
        final Session s = this.repository.loginAdministrative(null);
        final String path = "/" + uniqueName("dynamicHooks");
        try {
            final Node n = s.getRootNode().addNode(path.substring(1));
            n.setProperty("foo", "bar");
            s.save();
            // Since tests are run in parallel, sometimes another test may trigger the hook,
            // so check that it was invoked at least once
            assertTrue(TestCommitHook.getInvokedCount() >= 1);
            assertTrue(TestEditor.getInvokedCount() >= 1);
        } finally {
            reg.unregister();
            reg2.unregister();
            s.logout();
        }
    }

    private static final class TestCommitHook implements CommitHook
    {
        private static int invokedCount;

        @Override
        public NodeState processCommit(NodeState before, NodeState after, CommitInfo info)
            throws CommitFailedException
        {
            invokedCount++;
            return after;
        }

        public static int getInvokedCount()
        {
            return invokedCount;
        }
    }

    private static final class TestEditor extends DefaultEditor
    {
        private static int invokedCount;

        @Override
        public Editor childNodeAdded(String name, NodeState after)
            throws CommitFailedException
        {
            invokedCount++;
            return null;
        }

        public static int getInvokedCount()
        {
            return invokedCount;
        }
    }

    private static final class TestEditorProvider implements EditorProvider
    {
        @Override
        public Editor getRootEditor(NodeState before, NodeState after, NodeBuilder builder, CommitInfo info)
            throws CommitFailedException
        {
            return new TestEditor();
        }
    }
}
