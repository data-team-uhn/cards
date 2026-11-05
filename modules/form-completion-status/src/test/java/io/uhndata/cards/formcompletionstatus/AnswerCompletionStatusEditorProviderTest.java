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
package io.uhndata.cards.formcompletionstatus;

import java.util.List;

import javax.jcr.Session;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnswerCompletionStatusEditorProvider}.
 *
 * @version $Id$
 */
public class AnswerCompletionStatusEditorProviderTest
{
    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final AnswerCompletionStatusEditorProvider answerCompletionStatusEditorProvider =
        new AnswerCompletionStatusEditorProvider();

    private final ThreadResourceResolverProvider rrp = mock(ThreadResourceResolverProvider.class);

    private final FormUtils formUtils = mock(FormUtils.class);

    @Before
    public void setUp() throws IllegalAccessException
    {
        FieldUtils.writeField(this.answerCompletionStatusEditorProvider, "rrp", this.rrp, true);
        FieldUtils.writeField(this.answerCompletionStatusEditorProvider, "formUtils", this.formUtils, true);
    }

    @Test
    public void getRootEditorReturnsAnswerCompletionStatusEditor() throws CommitFailedException, IllegalAccessException
    {
        final ResourceResolver resourceResolver = this.context.resourceResolver();
        final Session session = resourceResolver.adaptTo(Session.class);

        NodeBuilder currentNodeBuilder = mock(NodeBuilder.class);
        when(this.formUtils.isForm(currentNodeBuilder)).thenReturn(true);
        when(this.rrp.getThreadResourceResolver()).thenReturn(resourceResolver);
        FieldUtils.writeField(this.answerCompletionStatusEditorProvider, "allValidators", List.of(), true);

        Editor editor = this.answerCompletionStatusEditorProvider.getRootEditor(mock(NodeState.class),
                mock(NodeState.class), currentNodeBuilder, new CommitInfo(session.toString(), session.getUserID()));
        assertNotNull(editor);
        assertTrue(editor instanceof AnswerCompletionStatusEditor);
    }

    @Test
    public void getRootEditorForNullResourceResolverReturnsNull() throws CommitFailedException
    {
        final ResourceResolver resourceResolver = this.context.resourceResolver();
        final Session session = resourceResolver.adaptTo(Session.class);
        when(this.rrp.getThreadResourceResolver()).thenReturn(null);

        Editor editor = this.answerCompletionStatusEditorProvider.getRootEditor(mock(NodeState.class),
                mock(NodeState.class), mock(NodeBuilder.class), new CommitInfo(session.toString(),
                        session.getUserID()));
        assertNull(editor);
    }

}
