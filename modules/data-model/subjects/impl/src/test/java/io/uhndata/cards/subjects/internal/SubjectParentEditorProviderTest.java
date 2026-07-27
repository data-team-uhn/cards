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
package io.uhndata.cards.subjects.internal;

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectParentEditorProvider}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectParentEditorProviderTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String QUESTIONNAIRE_TYPE = "cards:Questionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectParentEditorProvider subjectParentEditorProvider;

    @Test
    public void getRootEditorReturnsSubjectParentEditor() throws CommitFailedException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        NodeBuilder builder = mock(NodeBuilder.class);
        PropertyState propertyState = mock(PropertyState.class);
        when(builder.getProperty(NODE_TYPE)).thenReturn(propertyState);
        when(propertyState.getValue(Type.STRING)).thenReturn(QUESTIONNAIRE_TYPE);

        Editor editor = this.subjectParentEditorProvider.getRootEditor(mock(NodeState.class),
                mock(NodeState.class), builder, new CommitInfo(session.toString(), session.getUserID()));
        assertNotNull(editor);
        assertTrue(editor instanceof SubjectParentEditor);
    }
}
