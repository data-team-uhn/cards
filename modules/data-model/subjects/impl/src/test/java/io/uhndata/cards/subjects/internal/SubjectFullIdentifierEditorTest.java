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

import java.util.Stack;
import java.util.UUID;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectFullIdentifierEditor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectFullIdentifierEditorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String SUBJECT_TYPE_TYPE = "cards:SubjectType";
    private static final String TYPE_PROPERTY = "type";
    private static final String LABEL_PROPERTY = "label";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String ORDER_PROPERTY = "cards:defaultOrder";
    private static final String SUBJECT_LIST_LABEL_PROPERTY = "subjectListLabel";


    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private NodeBuilder currentNodeBuilder;
    @Mock
    private Stack<String> identifiers;
    private SubjectFullIdentifierEditor subjectFullIdentifierEditor;


    @Test
    public void constructorForSubjectNodeBuilder()
    {
        this.subjectFullIdentifierEditor = new SubjectFullIdentifierEditor(this.currentNodeBuilder, this.identifiers);
        verify(this.identifiers).push("Root");
        assertNotNull(this.subjectFullIdentifierEditor);
    }

    @Test
    public void constructorForNotSubjectNodeBuilder()
    {
        NodeBuilder nodeBuilder = mock(NodeBuilder.class);
        PropertyState property = mock(PropertyState.class);
        when(nodeBuilder.getProperty(NODE_TYPE)).thenReturn(property);
        when(property.getValue(Type.STRING)).thenReturn(SUBJECT_TYPE_TYPE);
        this.subjectFullIdentifierEditor = new SubjectFullIdentifierEditor(nodeBuilder, this.identifiers);
        verify(this.identifiers, times(0)).push(anyString());
        assertNotNull(this.subjectFullIdentifierEditor);
    }

    @Test
    public void childNodeAddedForActualSubjectNodeBuilder() throws CommitFailedException
    {
        this.subjectFullIdentifierEditor = new SubjectFullIdentifierEditor(this.currentNodeBuilder, this.identifiers);
        Editor editor = this.subjectFullIdentifierEditor.childNodeAdded("Branch", mock(NodeState.class));
        verify(this.identifiers).push("Branch");
        assertNotNull(editor);
        assertTrue(editor instanceof SubjectFullIdentifierEditor);
    }

    @Test
    public void childNodeChangedForActualSubjectNodeBuilder() throws CommitFailedException
    {
        this.subjectFullIdentifierEditor = new SubjectFullIdentifierEditor(this.currentNodeBuilder, this.identifiers);
        Editor editor = this.subjectFullIdentifierEditor.childNodeChanged("Branch", mock(NodeState.class),
                mock(NodeState.class));

        verify(this.identifiers).push("Branch");
        assertNotNull(editor);
        assertTrue(editor instanceof SubjectFullIdentifierEditor);
    }

    @Test
    public void leaveForActualSubjectNodeBuilder() throws CommitFailedException
    {
        this.subjectFullIdentifierEditor = new SubjectFullIdentifierEditor(this.currentNodeBuilder, this.identifiers);
        Whitebox.setInternalState(this.subjectFullIdentifierEditor, "identifiers", fillIdentifiersForRootSubject());
        this.subjectFullIdentifierEditor.leave(mock(NodeState.class), mock(NodeState.class));
        assertTrue(this.currentNodeBuilder.hasProperty("fullIdentifier"));
        assertEquals("Root", this.currentNodeBuilder.getString("fullIdentifier"));
    }

    @Before
    public void setupRepo()
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");

        NodeBuilder branchSubjectTypeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        String branchSubjectTypeUuid = UUID.randomUUID().toString();
        branchSubjectTypeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        branchSubjectTypeBuilder.setProperty(LABEL_PROPERTY, "Branch");
        branchSubjectTypeBuilder.setProperty(SUBJECT_LIST_LABEL_PROPERTY, "Branches");
        branchSubjectTypeBuilder.setProperty(ORDER_PROPERTY, 1);
        branchSubjectTypeBuilder.setProperty(NODE_IDENTIFIER, branchSubjectTypeUuid);

        NodeBuilder rootSubjectTypeBuilder = EmptyNodeState.EMPTY_NODE.builder();
        String rootSubjectTypeUuid = UUID.randomUUID().toString();
        rootSubjectTypeBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE_TYPE);
        rootSubjectTypeBuilder.setProperty(LABEL_PROPERTY, "Root");
        rootSubjectTypeBuilder.setProperty(SUBJECT_LIST_LABEL_PROPERTY, "Roots");
        rootSubjectTypeBuilder.setProperty(ORDER_PROPERTY, 0);
        rootSubjectTypeBuilder.setProperty(NODE_IDENTIFIER, rootSubjectTypeUuid);
        rootSubjectTypeBuilder.setChildNode(branchSubjectTypeUuid, branchSubjectTypeBuilder.getNodeState());

        NodeBuilder branchSubjectBuilder = EmptyNodeState.EMPTY_NODE.builder();
        String branchSubjectUuid = UUID.randomUUID().toString();
        branchSubjectBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        branchSubjectBuilder.setProperty(TYPE_PROPERTY, branchSubjectTypeUuid);
        branchSubjectBuilder.setProperty(IDENTIFIER_PROPERTY, "Branch");
        branchSubjectBuilder.setProperty(NODE_IDENTIFIER, branchSubjectUuid);

        NodeBuilder rootSubjectBuilder = EmptyNodeState.EMPTY_NODE.builder();
        String rootSubjectUuid = UUID.randomUUID().toString();
        rootSubjectBuilder.setProperty(NODE_TYPE, SUBJECT_TYPE);
        rootSubjectBuilder.setProperty(TYPE_PROPERTY, rootSubjectTypeUuid);
        rootSubjectBuilder.setProperty(IDENTIFIER_PROPERTY, "Root");
        rootSubjectBuilder.setProperty(NODE_IDENTIFIER, rootSubjectUuid);
        rootSubjectBuilder.setChildNode("Branch", branchSubjectBuilder.getNodeState());

        this.currentNodeBuilder = rootSubjectBuilder;
    }

    private Stack<String> fillIdentifiersForRootSubject()
    {
        Stack<String> filledIdentifiers = new Stack<>();
        filledIdentifiers.push("Root");
        return filledIdentifiers;
    }

}
