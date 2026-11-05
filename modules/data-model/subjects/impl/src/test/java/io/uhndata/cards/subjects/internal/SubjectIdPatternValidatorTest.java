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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectIdPatternValidator}.
 *
 * @version $Id$
 */
public class SubjectIdPatternValidatorTest
{
    private static final String IDENTIFIER_PROPERTY = "identifier";

    private static final String TYPE_PROPERTY = "type";

    private static final String ID_PATTERN_PROPERTY = "idPattern";

    private final SubjectTypeUtils subjectTypeUtils = mock(SubjectTypeUtils.class);

    private final SubjectUtils subjectUtils = mock(SubjectUtils.class);

    private final NodeState currentNode = mock(NodeState.class);

    private final Node subjectType = mock(Node.class);

    private SubjectIdPatternValidator validator;

    @Before
    public void setUp()
    {
        this.validator = new SubjectIdPatternValidator(this.subjectTypeUtils, this.subjectUtils, this.currentNode);
    }

    @Test
    public void propertyChangedValidatesIdentifierChangesOnSubjects() throws CommitFailedException,
        RepositoryException
    {
        mockSubject(this.currentNode, "AB123456");
        mockIdPattern("^[A-Z]{2}[0-9]+$");

        this.validator.propertyChanged(null, this.currentNode.getProperty(IDENTIFIER_PROPERTY));
    }

    @Test
    public void propertyChangedRejectsIdentifierNotMatchingThePattern() throws RepositoryException
    {
        mockSubject(this.currentNode, "wrong id");
        mockIdPattern("^[A-Z]{2}[0-9]+$");

        assertThrows(CommitFailedException.class,
            () -> this.validator.propertyChanged(null, this.currentNode.getProperty(IDENTIFIER_PROPERTY)));
    }

    @Test
    public void propertyChangedIgnoresOtherProperties() throws CommitFailedException
    {
        PropertyState property = mock(PropertyState.class);
        when(property.getName()).thenReturn("fullIdentifier");

        this.validator.propertyChanged(null, property);
    }

    @Test
    public void propertyChangedIgnoresNonSubjectNodes() throws CommitFailedException
    {
        PropertyState property = mock(PropertyState.class);
        when(property.getName()).thenReturn(IDENTIFIER_PROPERTY);
        when(this.subjectUtils.isSubject(this.currentNode)).thenReturn(false);

        this.validator.propertyChanged(null, property);
    }

    @Test
    public void childNodeChangedDescendsIntoChildren() throws CommitFailedException
    {
        Validator childValidator = this.validator.childNodeChanged("s1", mock(NodeState.class),
            mock(NodeState.class));
        assertNotNull(childValidator);
        assertNotSame(this.validator, childValidator);
        assertTrue(childValidator instanceof SubjectIdPatternValidator);
    }

    @Test
    public void childNodeAddedValidatesNewSubjects() throws CommitFailedException, RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "AB123456");
        mockIdPattern("^[A-Z]{2}[0-9]+$");

        assertSame(this.validator, this.validator.childNodeAdded("s1", child));
    }

    @Test
    public void childNodeAddedRejectsNewSubjectNotMatchingThePattern() throws RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "wrong id");
        mockIdPattern("^[A-Z]{2}[0-9]+$");

        assertThrows(CommitFailedException.class, () -> this.validator.childNodeAdded("s1", child));
    }

    @Test
    public void childNodeAddedIgnoresNonSubjectNodes() throws CommitFailedException
    {
        NodeState child = mock(NodeState.class);
        when(this.subjectUtils.isSubject(child)).thenReturn(false);

        assertSame(this.validator, this.validator.childNodeAdded("s1", child));
    }

    @Test
    public void subjectTypeWithoutIdPatternAcceptsAnyIdentifier() throws CommitFailedException, RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "anything goes");
        when(this.subjectTypeUtils.getSubjectType("subject-type-uuid")).thenReturn(this.subjectType);
        when(this.subjectType.hasProperty(ID_PATTERN_PROPERTY)).thenReturn(false);

        this.validator.childNodeAdded("s1", child);
    }

    @Test
    public void subjectTypeWithBlankIdPatternAcceptsAnyIdentifier() throws CommitFailedException, RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "anything goes");
        mockIdPattern(" ");

        this.validator.childNodeAdded("s1", child);
    }

    @Test
    public void subjectTypeWithInvalidIdPatternRejectsAnyIdentifier() throws RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "anything");
        mockIdPattern("[invalid");

        assertThrows(CommitFailedException.class, () -> this.validator.childNodeAdded("s1", child));
    }

    @Test
    public void validationCatchesRepositoryException() throws CommitFailedException, RepositoryException
    {
        NodeState child = mock(NodeState.class);
        mockSubject(child, "AB123456");
        when(this.subjectTypeUtils.getSubjectType("subject-type-uuid")).thenReturn(this.subjectType);
        when(this.subjectType.hasProperty(ID_PATTERN_PROPERTY)).thenThrow(new RepositoryException());

        this.validator.childNodeAdded("s1", child);
    }

    private void mockSubject(final NodeState subject, final String identifier)
    {
        when(this.subjectUtils.isSubject(subject)).thenReturn(true);
        PropertyState identifierProperty = mock(PropertyState.class);
        when(identifierProperty.getName()).thenReturn(IDENTIFIER_PROPERTY);
        when(identifierProperty.getValue(Type.STRING)).thenReturn(identifier);
        when(subject.getProperty(IDENTIFIER_PROPERTY)).thenReturn(identifierProperty);
        PropertyState typeProperty = mock(PropertyState.class);
        when(typeProperty.getValue(Type.REFERENCE)).thenReturn("subject-type-uuid");
        when(subject.getProperty(TYPE_PROPERTY)).thenReturn(typeProperty);
    }

    private void mockIdPattern(final String pattern) throws RepositoryException
    {
        when(this.subjectTypeUtils.getSubjectType("subject-type-uuid")).thenReturn(this.subjectType);
        when(this.subjectType.hasProperty(ID_PATTERN_PROPERTY)).thenReturn(true);
        Property idPattern = mock(Property.class);
        when(idPattern.getString()).thenReturn(pattern);
        when(this.subjectType.getProperty(ID_PATTERN_PROPERTY)).thenReturn(idPattern);
    }
}
