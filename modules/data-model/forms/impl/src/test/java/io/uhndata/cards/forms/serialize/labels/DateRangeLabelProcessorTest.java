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
package io.uhndata.cards.forms.serialize.labels;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.jackrabbit.value.DateValue;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DateRangeLabelProcessor}.
 *
 * @version $Id $
 */
@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class DateRangeLabelProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";

    private static final String FORM_TYPE = "cards:Form";

    private static final String SUBJECT_TYPE = "cards:Subject";

    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";

    private static final String ANSWER_DATE_TYPE = "cards:DateAnswer";

    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";

    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";

    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";

    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";

    private static final String TEST_FORM_PATH = "/Forms/f1";

    private static final String TEST_ANSWER_PATH = "/Forms/f1/s1/a1";

    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";

    private static final String QUESTION_PROPERTY = "question";

    private static final String SECTION_PROPERTY = "section";

    private static final String SUBJECT_PROPERTY = "subject";

    private static final String VALUE_PROPERTY = "value";

    private static final String TYPE_PROPERTY = "type";

    private static final String INTERVAL_TYPE = "interval";

    private static final String DISPLAYED_VALUE_PROPERTY = "displayedValue";

    // Must run after DateLabelProcessor (75) so the combined label overwrites the two-element array
    private static final int PRIORITY = 76;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DateRangeLabelProcessor dateRangeLabelProcessor;

    @Test
    public void getDescriptionReturnsSomething()
    {
        Assert.assertNotNull(this.dateRangeLabelProcessor.getDescription());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.dateRangeLabelProcessor.getPriority());
    }

    @Test
    public void leaveForIntervalAnswerCombinesDatesIntoSingleLabel() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        session.getNode(TEST_QUESTION_PATH).setProperty(TYPE_PROPERTY, INTERVAL_TYPE);
        Calendar start = Calendar.getInstance();
        start.set(2023, Calendar.JANUARY, 1);
        Calendar end = Calendar.getInstance();
        end.set(2023, Calendar.JANUARY, 5);
        Node node = session.getNode(TEST_ANSWER_PATH);
        node.setProperty(VALUE_PROPERTY, new DateValue[] {
            new DateValue(start), new DateValue(end)
        }, PropertyType.DATE);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dateRangeLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertTrue(jsonObject.containsKey(DISPLAYED_VALUE_PROPERTY));
        Assert.assertEquals("2023-01-01 — 2023-01-05", jsonObject.getString(DISPLAYED_VALUE_PROPERTY));
    }

    @Test
    public void leaveForIntervalAnswerWithYearDateFormatCombinesRawValues() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node question = session.getNode(TEST_QUESTION_PATH);
        question.setProperty(TYPE_PROPERTY, INTERVAL_TYPE);
        question.setProperty("dateFormat", "yyyy");
        Calendar start = Calendar.getInstance();
        start.set(2023, Calendar.JANUARY, 1);
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 1);
        Node node = session.getNode(TEST_ANSWER_PATH);
        node.setProperty(VALUE_PROPERTY, new DateValue[] {
            new DateValue(start), new DateValue(end)
        }, PropertyType.DATE);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dateRangeLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Assert.assertEquals(format.format(start.getTime()) + " — " + format.format(end.getTime()),
            jsonObject.getString(DISPLAYED_VALUE_PROPERTY));
    }

    @Test
    public void leaveForNonIntervalQuestionAddsNothing() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Calendar start = Calendar.getInstance();
        start.set(2023, Calendar.JANUARY, 1);
        Calendar end = Calendar.getInstance();
        end.set(2023, Calendar.JANUARY, 5);
        Node node = session.getNode(TEST_ANSWER_PATH);
        node.setProperty(VALUE_PROPERTY, new DateValue[] {
            new DateValue(start), new DateValue(end)
        }, PropertyType.DATE);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dateRangeLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Test
    public void leaveForSingleDatePassesLabelThroughUnchanged() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        session.getNode(TEST_QUESTION_PATH).setProperty(TYPE_PROPERTY, INTERVAL_TYPE);
        Calendar date = Calendar.getInstance();
        date.set(2023, Calendar.JANUARY, 1);
        Node node = session.getNode(TEST_ANSWER_PATH);
        node.setProperty(VALUE_PROPERTY, new DateValue(date), PropertyType.DATE);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dateRangeLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();

        Assert.assertEquals("2023-01-01", jsonObject.getString(DISPLAYED_VALUE_PROPERTY));
    }

    @Test
    public void leaveForNotDateAnswerNodeThrowsException() throws RepositoryException
    {
        JsonObjectBuilder json = Json.createObjectBuilder();
        Node node = mock(Node.class);
        when(node.isNodeType(ANSWER_DATE_TYPE)).thenThrow(new RepositoryException());

        this.dateRangeLabelProcessor.leave(node, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertTrue(jsonObject.isEmpty());
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
            .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
            .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
            .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
            .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
            .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
            .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
            .commit();

        Session session = this.context.resourceResolver().adaptTo(Session.class);

        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node section = session.getNode(TEST_SECTION_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);

        this.context.build()
            .resource(TEST_FORM_PATH,
                NODE_TYPE, FORM_TYPE,
                QUESTIONNAIRE_PROPERTY, questionnaire,
                SUBJECT_PROPERTY, subject,
                "relatedSubjects", List.of(subject).toArray())
            .resource("/Forms/f1/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, section)
            .resource(TEST_ANSWER_PATH,
                NODE_TYPE, ANSWER_DATE_TYPE,
                QUESTION_PROPERTY, question)
            .commit();
    }
}
