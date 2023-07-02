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

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.serialize.ResourceToJsonAdapterFactory;
import io.uhndata.cards.spi.QuickSearchEngine;
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
    private static final String TEST_REFERENCE_CALENDAR_QUESTIONNAIRE_PATH =
            "/Questionnaires/TestCalendarReferenceQuestionnaire";
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

        QuickSearchEngine.Results output =
                this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 4; numberOfFoundMatches++) {
            Assert.assertTrue(output.hasNext());
            Assert.assertNotNull(output.next());
        }
        Assert.assertFalse(output.hasNext());
    }

    @Test
    public void quickSearchForQuestionText()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("Long Question")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output =
                this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 3; numberOfFoundMatches++) {
            Assert.assertTrue(output.hasNext());
            Assert.assertNotNull(output.next());
        }
        Assert.assertFalse(output.hasNext());
    }

    @Test
    public void quickSearchForQuestionnaireTitle()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("Reference Questionnaire")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output =
                this.questionnaireQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 2; numberOfFoundMatches++) {
            Assert.assertTrue(output.hasNext());
            Assert.assertNotNull(output.next());
        }
        Assert.assertFalse(output.hasNext());
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
        this.context.registerService(AdapterFactory.class, new ResourceToJsonAdapterFactory());
        this.context.registerAdapter(Resource.class, JsonObject.class, Json.createObjectBuilder().build());
    }

}
