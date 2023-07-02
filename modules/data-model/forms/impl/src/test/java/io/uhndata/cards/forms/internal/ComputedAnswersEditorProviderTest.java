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
package io.uhndata.cards.forms.internal;

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
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

import io.uhndata.cards.forms.api.ExpressionUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ComputedAnswersEditorProvider}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ComputedAnswersEditorProviderTest
{

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private ComputedAnswersEditorProvider computedAnswersEditorProvider;

    @Mock
    private ResourceResolverFactory rrf;

    @Mock
    private ThreadResourceResolverProvider rrp;

    @Mock
    private QuestionnaireUtils questionnaireUtils;

    @Mock
    private FormUtils formUtils;

    @Mock
    private ExpressionUtils expressionUtils;

    @Test
    public void getRootEditorReturnsComputedAnswersEditorForNotNullResourceResolver() throws CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        when(this.rrp.getThreadResourceResolver()).thenReturn(this.context.resourceResolver());
        Editor editor = this.computedAnswersEditorProvider.getRootEditor(Mockito.mock(NodeState.class),
                Mockito.mock(NodeState.class), Mockito.mock(NodeBuilder.class), new CommitInfo(session.toString(),
                        session.getUserID()));
        Assert.assertNotNull(editor);
        Assert.assertTrue(editor instanceof ComputedAnswersEditor);
    }

    @Test
    public void getRootEditorReturnsComputedAnswersEditorForNullResourceResolver() throws CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        Editor editor = this.computedAnswersEditorProvider.getRootEditor(Mockito.mock(NodeState.class),
                Mockito.mock(NodeState.class), Mockito.mock(NodeBuilder.class), new CommitInfo(session.toString(),
                        session.getUserID()));
        Assert.assertNull(editor);
    }

}
