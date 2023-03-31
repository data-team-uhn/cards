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
package io.uhndata.cards;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceIterator}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceIteratorTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private ResourceIterator resourceIterator;

    @Test
    public void hasNextReturnsTrueForAllNodeChildren()
    {
        for (int i = 0; i < 8; i++) {
            assertTrue(this.resourceIterator.hasNext());
            this.resourceIterator.next();
        }
        assertFalse(this.resourceIterator.hasNext());
    }

    @Test
    public void nextReturnsResource()
    {
        Resource resource = this.resourceIterator.next();
        assertNotNull(resource);
    }

    @Test
    public void nextCatchesRepositoryExceptionAndReturnsNull() throws RepositoryException
    {
        NodeIterator nodeIterator = mock(NodeIterator.class);
        Node node = mock(Node.class);
        when(nodeIterator.nextNode()).thenReturn(node);
        when(node.getPath()).thenThrow(new RepositoryException());
        this.resourceIterator = new ResourceIterator(this.context.resourceResolver(), nodeIterator);
        Resource resource = this.resourceIterator.next();
        assertNull(resource);
    }

    @Test
    public void removeThrowsUnsupportedOperationException()
    {
        assertThrows(UnsupportedOperationException.class, () -> this.resourceIterator.remove());
    }

    @Before
    public void setUp() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", "jcr:primaryType", "cards:QuestionnairesHomepage")
                .commit();

        this.context.load().json("/Questionnaires.json", "/Questionnaires/TestSerializableQuestionnaire");

        ResourceResolver rr = this.context.resourceResolver();
        NodeIterator nodeIterator = rr.adaptTo(Session.class).getNode("/Questionnaires/TestSerializableQuestionnaire")
                .getNodes();
        this.resourceIterator = new ResourceIterator(rr, nodeIterator);
    }
}
