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
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RootRestrictionFactory}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class RootRestrictionFactoryTest
{
    public static final String NAME = "cards:root";
    public static final Type<?> TYPE = Type.STRINGS;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private RootRestrictionFactory rootRestrictionFactory;

    @Test
    public void getNameTest()
    {
        assertEquals(NAME, this.rootRestrictionFactory.getName());
    }

    @Test
    public void getTypeTest()
    {
        assertEquals(TYPE, this.rootRestrictionFactory.getType());
    }

    @Test
    public void forValueTest()
    {
        RestrictionPattern restrictionPattern = this.rootRestrictionFactory.forValue(mock(PropertyState.class));
        assertNotNull(restrictionPattern);
        assertTrue(restrictionPattern instanceof RootRestrictionPattern);
    }

}
