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
package io.uhndata.cards.formcompletionstatus.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MinMaxValueValidator}.
 *
 * @version $Id$
 */
public class MinMaxValueValidatorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String NODE_IDENTIFIER = "jcr:uuid";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String QUESTION_PROPERTY = "question";
    private static final String VALUE_PROPERTY = "value";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String FLAG_INVALID = "INVALID";
    private static final int PRIORITY = 50;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final MinMaxValueValidator minMaxValueValidator = new MinMaxValueValidator();

    @Test
    public void getPriorityReturnsValidatorPriority()
    {
        assertEquals(PRIORITY, this.minMaxValueValidator.getPriority());
    }

    @Test
    public void validateForAnswerOfNotSupportedType() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/question_1");
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, question.getIdentifier());

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answerInSectionNodeBuilder, question, flags);
        assertTrue(flags.containsKey(FLAG_INVALID));
        assertFalse(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateForAnswerWithAllowedValueProperty() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2");
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, question.getIdentifier());
        answerInSectionNodeBuilder.setProperty(VALUE_PROPERTY, Set.of("100", "200"), Type.STRINGS);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answerInSectionNodeBuilder, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Test
    public void validateForAnswerWithTooSmallValueProperty() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2");
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, question.getIdentifier());
        answerInSectionNodeBuilder.setProperty(VALUE_PROPERTY, Set.of("40"), Type.STRINGS);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answerInSectionNodeBuilder, question, flags);
        assertTrue(flags.containsKey(FLAG_INVALID));
        assertTrue(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateForAnswerWithTooBigValueProperty() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_2");
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, question.getIdentifier());
        answerInSectionNodeBuilder.setProperty(VALUE_PROPERTY, Set.of("3000"), Type.STRINGS);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answerInSectionNodeBuilder, question, flags);
        assertTrue(flags.containsKey(FLAG_INVALID));
        assertTrue(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateCatchesRepositoryException() throws RepositoryException
    {
        Node question = mock(Node.class);
        when(question.getProperty("dataType")).thenThrow(new RepositoryException());
        String answerInSectionUuid = UUID.randomUUID().toString();
        NodeBuilder answerInSectionNodeBuilder = createTestAnswer(answerInSectionUuid, UUID.randomUUID().toString());

        this.minMaxValueValidator.validate(answerInSectionNodeBuilder, question, new HashMap<>());
    }

    @Test
    public void validateForOutOfRangeAnswerOptionValueRemovesInvalidFlag() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/question_6");
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), question.getIdentifier());
        // -1 is below the minimum value, but it is one of the predefined answer options, so it is accepted
        answer.setProperty(VALUE_PROPERTY, -1L);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answer, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Test
    public void validateForOutOfRangeValueNotMatchingOptionsSetsInvalidFlag() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/question_6");
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, 10L);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answer, question, flags);
        assertTrue(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateForQuestionWithoutLimitsRemovesInvalidFlag() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/question_5");
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, 10L);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answer, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Test
    public void validateForSuggestedLimitsLeavesFlagsUntouched() throws RepositoryException
    {
        Node question = this.context.resourceResolver().adaptTo(Session.class)
                .getNode(TEST_QUESTIONNAIRE_PATH + "/question_7");
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, 10L);

        // The limits are only suggested, not enforced, so the out-of-range value is not flagged
        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answer, question, flags);
        assertTrue(flags.containsKey(FLAG_INVALID));
        assertFalse(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateCatchesRepositoryExceptionReadingLimits() throws RepositoryException
    {
        Node question = mock(Node.class);
        Property dataType = mock(Property.class);
        when(question.getProperty("dataType")).thenReturn(dataType);
        when(dataType.getString()).thenReturn("long");
        when(question.hasProperty("minValue")).thenReturn(true);
        when(question.getProperty("minValue")).thenThrow(new RepositoryException());
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        answer.setProperty(VALUE_PROPERTY, 10L);

        this.minMaxValueValidator.validate(answer, question, new HashMap<>());
    }

    @Test
    public void validateCatchesRepositoryExceptionReadingOptions() throws RepositoryException
    {
        Node question = mock(Node.class);
        Property dataType = mock(Property.class);
        when(question.getProperty("dataType")).thenReturn(dataType);
        when(dataType.getString()).thenReturn("long");
        when(question.getNodes()).thenThrow(new RepositoryException());
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        answer.setProperty(VALUE_PROPERTY, 10L);

        // Failing to read the answer options is treated as having no options
        Map<String, Boolean> flags = createStatusFlagsMap();
        this.minMaxValueValidator.validate(answer, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Before
    public void setupRepo()
    {
        this.context.build().resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage").commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
    }

    private Map<String, Boolean> createStatusFlagsMap()
    {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(FLAG_INVALID, false);
        return flags;
    }

    private NodeBuilder createTestAnswer(String uuid, String questionUuid)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        answerBuilder.setProperty(NODE_IDENTIFIER, uuid);

        return answerBuilder;
    }

}
