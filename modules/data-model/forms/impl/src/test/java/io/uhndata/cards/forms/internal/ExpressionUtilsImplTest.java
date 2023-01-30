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

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link ExpressionUtilsImpl}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class ExpressionUtilsImplTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private ExpressionUtilsImpl expressionUtils;

    @Mock
    private ScriptEngineManager manager;

    @Test
    public void getDependenciesForActualQuestionReturnsCorrectValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestComputedQuestionnaire/question_4");
        Set<String> expectedDependencies = Set.of("question_1", "question_2", "question_3");
        Set<String> dependencies = this.expressionUtils.getDependencies(question);
        Assert.assertEquals(3, dependencies.size());
        Assert.assertEquals(dependencies, expectedDependencies);
    }

    @Test
    public void getExpressionFromQuestionForActualQuestionReturnsCorrectValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestComputedQuestionnaire/question_4");
        String expressionActual = this.expressionUtils.getExpressionFromQuestion(question);
        Assert.assertNotNull(expressionActual);
        Assert.assertEquals(
            "return (@{question_3:-true} ? @{question_1:-200} + @{question_2:-100} : @{question_1})",
            expressionActual);
    }

    @Test
    public void getExpressionFromQuestionForNotComputedQuestionReturnsEmptyValue() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestComputedQuestionnaire/question_1");
        String expressionActual = this.expressionUtils.getExpressionFromQuestion(question);
        Assert.assertNotNull(expressionActual);
        Assert.assertTrue(expressionActual.isEmpty());
    }

    @Test
    public void evaluateForComplicateComputedQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode("/Questionnaires/TestComputedQuestionnaire/question_4");
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Mockito.when(engine.createBindings()).thenReturn(new SimpleBindings());
        Mockito.when(engine.eval(Mockito.eq("(function(){return (arg0 ? arg1 + arg2 : arg1)})()"),
            Mockito.any(Bindings.class))).thenReturn(300L);
        Object computedAnswer = this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.LONG);
        Assert.assertNotNull(computedAnswer);
        Assert.assertEquals(300L, computedAnswer);
    }

    @Test
    public void evaluateFromDoubleQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        Object result = 100.7;
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_double_to_computed_section/double_computed_question");
        Bindings filledBindings = emptyBindings;
        filledBindings.put("arg0", String.valueOf(result));
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(filledBindings)))
                .thenReturn(result);
        Assert.assertEquals(result, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DOUBLE));
        Assert.assertEquals(100L, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.LONG));
        Assert.assertEquals(new BigDecimal("100.7"),
            this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DECIMAL));
        Assert.assertEquals("100.7", this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

        question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_double_to_computed_section/double_long_computed_question");
        filledBindings.put("arg0", "100.0");
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(filledBindings)))
            .thenReturn(100.0);
        Assert.assertEquals("100", this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

    }

    @Test
    public void evaluateFromLongQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        Object result = 100L;
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/computed_question");
        Bindings bindings = emptyBindings;
        bindings.put("arg0", String.valueOf(result));
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(result);
        Assert.assertEquals(result, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.LONG));
    }

    @Test
    public void evaluateFromDecimalQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        Object result = new BigDecimal("100.0");
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_decimal_to_computed_section/computed_question");
        Bindings bindings = emptyBindings;
        bindings.put("arg0", String.valueOf(result));
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(result);
        Assert.assertEquals(result, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DECIMAL));
    }

    @Test
    public void evaluateFromNumberTextQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        String result = "100";
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_text_to_computed_section/number_computed_question");
        Bindings bindings = emptyBindings;
        bindings.put("arg0", result);
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(result);
        Assert.assertEquals(100L, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.LONG));
        Assert.assertEquals(100.0, this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DOUBLE));
        Assert.assertEquals(new BigDecimal(result),
            this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DECIMAL));
    }

    @Test
    public void evaluateFromDateTextQuestionReturnsCorrectValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1);
        Bindings bindings = emptyBindings;
        bindings.put("arg0", "2023-01-01");
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_text_to_computed_section/date_computed_question");

        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(calendar);
        Assert.assertEquals(DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(calendar.getTime().toInstant().atZone(ZoneId.systemDefault())),
            this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings)))
            .thenReturn(calendar.getTime());
        Assert.assertEquals(DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(calendar.getTime().toInstant().atZone(ZoneId.systemDefault())),
            this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

    }

    @Test
    public void evaluateProducesExceptionQuestionReturnsEmptyValue() throws RepositoryException, ScriptException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        ScriptEngine engine = Mockito.mock(ScriptEngine.class);
        Mockito.when(this.manager.getEngineByName("JavaScript")).thenReturn(engine);
        Bindings emptyBindings = new SimpleBindings();
        Mockito.when(engine.createBindings()).thenReturn(emptyBindings);

        Date result = new Date();
        Node question = session.getNode(
            "/Questionnaires/TestComputedQuestionnaire/from_text_to_computed_section/date_computed_question");
        Bindings bindings = emptyBindings;
        bindings.put("arg0", result);
        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(result);
        Assert.assertNull(this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.LONG));
        Assert.assertNull(this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DOUBLE));
        Assert.assertNull(this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.DECIMAL));

        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings))).thenReturn(null);
        Assert.assertNull(this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

        Mockito.when(engine.eval(Mockito.eq("(function(){return arg0})()"), Mockito.eq(bindings)))
            .thenThrow(new ScriptException("Evaluating the expression for question failed"));
        Assert.assertNull(this.expressionUtils.evaluate(question, Collections.emptyMap(), Type.STRING));

    }

    @Before
    public void setupRepo()
    {
        this.context.build()
            .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
            .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
            .commit();
        this.context.load().json("/ComputedQuestionnaires.json", TEST_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
            .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            .commit();
    }

}
