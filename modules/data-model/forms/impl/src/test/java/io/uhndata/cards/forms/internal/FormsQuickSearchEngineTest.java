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

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.spi.QuickSearchEngine;
import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchParametersFactory;

/**
 * Unit tests for {@link FormsQuickSearchEngine}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class FormsQuickSearchEngineTest
{

    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEXT_ANSWER_TYPE = "cards:TextAnswer";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_TEXT_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextComputedQuestionnaire";
    private static final String TEST_SECTION_PATH =
            "/Questionnaires/TestTextComputedQuestionnaire/from_text_to_computed_section";
    private static final String TEST_TEXT_QUESTION_PATH =
            "/Questionnaires/TestTextComputedQuestionnaire/from_text_to_computed_section/text_question";
    private static final String TEST_TEXT_COMPUTED_QUESTION_PATH =
            "/Questionnaires/TestTextComputedQuestionnaire/from_text_to_computed_section/computed_question";
    private static final String TEST_REFERENCE_QUESTIONNAIRE_PATH = "/Questionnaires/TestReferenceQuestionnaire";
    private static final String TEST_REFERENCE_SECTION_PATH =
            "/Questionnaires/TestReferenceQuestionnaire/reference_section";
    private static final String TEST_REFERENCE_QUESTION_PATH =
            "/Questionnaires/TestReferenceQuestionnaire/reference_section/reference_question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String SECTION_PROPERTY = "section";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String VALUE_PROPERTY = "value";
    private static final String QUICK_SEARCH_PARAMETER_TYPE = "quick";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private FormsQuickSearchEngine formsQuickSearchEngine;

    @Mock
    private FormUtils formUtils;

    @Test
    public void getSupportedTypesReturnsFormTypeList()
    {
        List<String> actualSupportedTypes = this.formsQuickSearchEngine.getSupportedTypes();
        Assert.assertEquals(1, actualSupportedTypes.size());
        Assert.assertEquals(FORM_TYPE, actualSupportedTypes.get(0));
    }

    @Test
    public void quickSearchAddsAllFoundFormsToOutput() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Mockito.when(this.formUtils.getValue(Mockito.any(Node.class)))
                .thenReturn("textAnswer1", "newValue", "textAnswer3");
        Mockito.when(this.formUtils.getForm(Mockito.any(Node.class))).thenReturn(
                session.getNode("/Forms/f1/s1/a1"),
                session.getNode("/Forms/f2/s1/a1"),
                session.getNode("/Forms/f3/s1/a1"));

        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("textAnswer")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output = this.formsQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 3; numberOfFoundMatches++) {
            Assert.assertTrue(output.hasNext());
            Assert.assertNotNull(output.next());
        }
        Assert.assertFalse(output.hasNext());
    }

    @Test
    public void quickSearchCatchesRepositoryExceptionForQuestionnairesWithoutTextProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);

        session.getNode(TEST_TEXT_COMPUTED_QUESTION_PATH).getProperty("text").remove();
        session.getNode(TEST_TEXT_QUESTION_PATH).getProperty("text").remove();
        session.getNode(TEST_REFERENCE_QUESTION_PATH).getProperty("text").remove();

        Mockito.when(this.formUtils.getValue(Mockito.any(Node.class)))
                .thenReturn("textAnswer1", "newValue", "textAnswer3");
        Mockito.when(this.formUtils.getForm(Mockito.any(Node.class))).thenReturn(
                session.getNode("/Forms/f1/s1/a1"),
                session.getNode("/Forms/f2/s1/a1"),
                session.getNode("/Forms/f3/s1/a1"));

        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("textAnswer")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output = this.formsQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 3; numberOfFoundMatches++) {
            Assert.assertTrue(output.hasNext());
            Assert.assertTrue(output.next().isEmpty());
        }
        Assert.assertFalse(output.hasNext());

    }

    @Test
    public void quickSearchReturnsEmptyResults() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Mockito.when(this.formUtils.getValue(Mockito.any(Node.class)))
                .thenReturn("textAnswer1", "newValue", "textAnswer3");
        Mockito.when(this.formUtils.getForm(Mockito.any(Node.class))).thenReturn(
                session.getNode("/Forms/f1/s1/a1"),
                session.getNode("/Forms/f2/s1/a1"),
                session.getNode("/Forms/f3/s1/a1"));

        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("nonexistentValue")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output = this.formsQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        Assert.assertFalse(output.hasNext());

    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);

        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/TextComputedQuestionnairesPlain.json", TEST_TEXT_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/ReferenceQuestionnaires.json", TEST_REFERENCE_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();
        this.context.registerAdapter(Resource.class, JsonObject.class, Json.createObjectBuilder().build());

        Node computedQuestionnaire = session.getNode(TEST_TEXT_COMPUTED_QUESTIONNAIRE_PATH);
        Node referenceQuestionnaire = session.getNode(TEST_REFERENCE_QUESTIONNAIRE_PATH);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node section = session.getNode(TEST_SECTION_PATH);
        Node referenceSection = session.getNode(TEST_REFERENCE_SECTION_PATH);
        Node textQuestionNode = session.getNode(TEST_TEXT_QUESTION_PATH);
        Node textComputedQuestionNode = session.getNode(TEST_TEXT_COMPUTED_QUESTION_PATH);
        Node referenceQuestionNode = session.getNode(TEST_REFERENCE_QUESTION_PATH);

        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                // Computed Form with 2 matched answers
                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, computedQuestionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1",
                        NODE_TYPE, TEXT_ANSWER_TYPE,
                        QUESTION_PROPERTY, textQuestionNode,
                        VALUE_PROPERTY, "textAnswer1")
                .resource("/Forms/f1/s1/a2",
                        NODE_TYPE, TEXT_ANSWER_TYPE,
                        QUESTION_PROPERTY, textComputedQuestionNode,
                        VALUE_PROPERTY, "notValue")

                // Computed Form with no matched answer
                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, computedQuestionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f2/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, section)
                .resource("/Forms/f2/s1/a1",
                        NODE_TYPE, TEXT_ANSWER_TYPE,
                        QUESTION_PROPERTY, textQuestionNode,
                        "note", "textAnswer2",
                        VALUE_PROPERTY, "notValue")

                // Reference Form with 1 matched answer
                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, referenceQuestionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f3/s1",
                        NODE_TYPE, ANSWER_SECTION_TYPE,
                        SECTION_PROPERTY, referenceSection)
                .resource("/Forms/f3/s1/a1",
                        NODE_TYPE, TEXT_ANSWER_TYPE,
                        QUESTION_PROPERTY, referenceQuestionNode,
                        VALUE_PROPERTY, "textAnswer3")
                .commit();
    }
}
