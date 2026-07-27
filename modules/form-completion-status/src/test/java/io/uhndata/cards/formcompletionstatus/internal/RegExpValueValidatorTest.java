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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegExpValueValidator}.
 *
 * @version $Id$
 */
public class RegExpValueValidatorTest
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

    private final RegExpValueValidator regExpValueValidator = new RegExpValueValidator();

    @Test
    public void getPriorityReturnsValidatorPriority()
    {
        assertEquals(PRIORITY, this.regExpValueValidator.getPriority());
    }

    @Test
    public void validateForMatchingValueRemovesInvalidFlag() throws RepositoryException
    {
        Node question = getQuestion("question_4");
        NodeBuilder answer = createTestAnswer(question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, "AB123456");

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.regExpValueValidator.validate(answer, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Test
    public void validateForNotMatchingValueSetsInvalidFlag() throws RepositoryException
    {
        Node question = getQuestion("question_4");
        NodeBuilder answer = createTestAnswer(question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, "wrong value");

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.regExpValueValidator.validate(answer, question, flags);
        assertTrue(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateForOneOfSeveralValuesNotMatchingSetsInvalidFlag() throws RepositoryException
    {
        Node question = getQuestion("question_4");
        NodeBuilder answer = createTestAnswer(question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, Set.of("AB123456", "wrong value"), Type.STRINGS);

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.regExpValueValidator.validate(answer, question, flags);
        assertTrue(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateForAnswerWithoutValueRemovesInvalidFlag() throws RepositoryException
    {
        Node question = getQuestion("question_4");
        NodeBuilder answer = createTestAnswer(question.getIdentifier());

        Map<String, Boolean> flags = createStatusFlagsMap();
        this.regExpValueValidator.validate(answer, question, flags);
        assertFalse(flags.containsKey(FLAG_INVALID));
    }

    @Test
    public void validateForNonTextQuestionLeavesFlagsUntouched() throws RepositoryException
    {
        Node question = getQuestion("question_1");
        NodeBuilder answer = createTestAnswer(question.getIdentifier());
        answer.setProperty(VALUE_PROPERTY, "anything");

        // question_1 is a boolean question, the validator only considers text questions
        Map<String, Boolean> flags = createStatusFlagsMap();
        this.regExpValueValidator.validate(answer, question, flags);
        assertTrue(flags.containsKey(FLAG_INVALID));
        assertFalse(flags.get(FLAG_INVALID));
    }

    @Test
    public void validateCatchesRepositoryException() throws RepositoryException
    {
        Node question = mock(Node.class);
        when(question.getProperty("dataType")).thenThrow(new RepositoryException());
        NodeBuilder answer = createTestAnswer(UUID.randomUUID().toString());

        this.regExpValueValidator.validate(answer, question, new HashMap<>());
    }

    @Before
    public void setupRepo()
    {
        this.context.build().resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage").commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
    }

    private Node getQuestion(final String name) throws RepositoryException
    {
        return this.context.resourceResolver().adaptTo(Session.class)
            .getNode(TEST_QUESTIONNAIRE_PATH + "/" + name);
    }

    private Map<String, Boolean> createStatusFlagsMap()
    {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(FLAG_INVALID, false);
        return flags;
    }

    private NodeBuilder createTestAnswer(String questionUuid)
    {
        NodeBuilder answerBuilder = EmptyNodeState.EMPTY_NODE.builder();
        answerBuilder.setProperty(NODE_TYPE, ANSWER_TYPE);
        answerBuilder.setProperty(QUESTION_PROPERTY, questionUuid);
        answerBuilder.setProperty(NODE_IDENTIFIER, UUID.randomUUID().toString());
        return answerBuilder;
    }
}
