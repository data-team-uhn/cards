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
package io.uhndata.cards.permissions.internal;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RootRestrictionPattern}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class RootRestrictionPatternTest
{
    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private RootRestrictionPattern rootRestrictionPattern;

    @Test
    public void matchesForTreeReturnsTrue()
    {
        Tree tree = mock(Tree.class);
        when(tree.getPath()).thenReturn("/");
        assertTrue(this.rootRestrictionPattern.matches(tree, mock(PropertyState.class)));
    }

    @Test
    public void matchesForPathReturnsFalse()
    {
        assertFalse(this.rootRestrictionPattern.matches("/Forms"));
    }

    @Test
    public void matchesForPathReturnsTrue()
    {
        assertTrue(this.rootRestrictionPattern.matches("/"));
    }

    @Test
    public void matchesReturnsFalse()
    {
        assertFalse(this.rootRestrictionPattern.matches());
    }

}
