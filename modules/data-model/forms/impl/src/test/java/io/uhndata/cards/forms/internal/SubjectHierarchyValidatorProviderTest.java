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
package io.uhndata.cards.forms.internal;

import javax.jcr.Session;

import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Unit tests for {@link SubjectHierarchyValidatorProvider}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectHierarchyValidatorProviderTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectHierarchyValidatorProvider subjectHierarchyValidatorProvider;

    @Mock
    private SubjectTypeUtils subjectTypeUtils;

    @Mock
    private SubjectUtils subjectUtils;

    @Test
    public void getRootValidatorReturnsSubjectHierarchyValidator()
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        Validator validator = this.subjectHierarchyValidatorProvider.getRootValidator(Mockito.mock(NodeState.class),
                Mockito.mock(NodeState.class), new CommitInfo(session.toString(), session.getUserID()));
        Assert.assertNotNull(validator);
        Assert.assertTrue(validator instanceof SubjectHierarchyValidator);
    }
}
