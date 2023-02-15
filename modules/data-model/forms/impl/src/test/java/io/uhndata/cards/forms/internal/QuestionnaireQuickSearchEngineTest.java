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

import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.json.JsonObject;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchParametersFactory;

/**
 * Unit tests for {@link QuestionnaireQuickSearchEngine}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireQuickSearchEngineTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String QUESTIONNAIRE_TYPE = "cards:Questionnaire";
    private static final String TEST_MATRIX_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionMatrixQuestionnaire";
    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";
    private static final String TEST_REFERENCE_CALENDAR_QUESTIONNAIRE_PATH = "/Questionnaires/TestCalendarReferenceQuestionnaire";
    private static final String TEST_REFERENCE_QUESTIONNAIRE_PATH = "/Questionnaires/TestReferenceQuestionnaire";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String QUICK_SEARCH_PARAMETER_TYPE = "quick";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private QuestionnaireQuickSearchEngine questionnaireQuickSearchEngine;

    @Test
    public void getSupportedTypesReturnsFormTypeList()
    {
        List<String> actualSupportedTypes = this.questionnaireQuickSearchEngine.getSupportedTypes();
        Assert.assertEquals(1, actualSupportedTypes.size());
        Assert.assertEquals(QUESTIONNAIRE_TYPE, actualSupportedTypes.get(0));
    }

    @Test
    public void quickSearchForOptionValue()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("O1")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        // find count of questions
        Assert.assertEquals(3, output.size());
    }

    @Test
    public void quickSearchForQuestionText()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("Long Question")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        // find count of questions
        Assert.assertEquals(3, output.size());
    }

    @Test
    public void quickSearchForQuestionnaireTitle()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("Reference Questionnaire")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        // find count of questionnaires
        Assert.assertEquals(2, output.size());
    }

    @Test
    public void quickSearchForMaxResultLessThanNumberOfMatches()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("Long Question")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .withMaxResults(1)
                .withShowTotalResults(false)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        Assert.assertEquals(1, output.size());
    }

    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .commit();
        this.context.load().json("/MatrixQuestionnaires.json", TEST_MATRIX_QUESTIONNAIRE_PATH);
        this.context.load().json("/ComputedQuestionnairesPlain.json", TEST_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/ReferenceQuestionnaires.json", TEST_REFERENCE_QUESTIONNAIRE_PATH);
        this.context.load()
                .json("/reference/ReferenceCalendarQuestionnaires.json", TEST_REFERENCE_CALENDAR_QUESTIONNAIRE_PATH);
    }

}
