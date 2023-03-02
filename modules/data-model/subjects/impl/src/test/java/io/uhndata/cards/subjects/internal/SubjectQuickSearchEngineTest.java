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

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.spi.SearchParameters;
import io.uhndata.cards.spi.SearchParametersFactory;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectQuickSearchEngine}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectQuickSearchEngineTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String QUICK_SEARCH_PARAMETER_TYPE = "quick";


    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectQuickSearchEngine subjectQuickSearchEngine;

    @Test
    public void getSupportedTypesReturnsSubjectTypeList()
    {
        List<String> actualSupportedTypes = this.subjectQuickSearchEngine.getSupportedTypes();
        assertEquals(1, actualSupportedTypes.size());
        assertEquals(SUBJECT_TYPE, actualSupportedTypes.get(0));
    }

    @Test
    public void quickSearchAddsAllFoundFormsToOutput()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("branch")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.subjectQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        assertEquals(2, output.size());
    }

    @Test
    public void quickSearchForMaxSizeOutputAndQueryWithShowTotalResultsFalse()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("branch")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .withShowTotalResults(false)
                .build();

        List<JsonObject> output = mock(List.class);
        when(output.size()).thenReturn(10);

        this.subjectQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        assertEquals(10, output.size());
    }

    @Test
    public void quickSearchForMaxResultLessThanNumberOfMatches()
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("root")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .withMaxResults(1)
                .withShowTotalResults(false)
                .build();

        List<JsonObject> output = new ArrayList<>();

        this.subjectQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        assertEquals(1, output.size());
    }

    @Test
    public void quickSearchCatchesRepositoryExceptionForQuestionnairesWithoutTextProperty() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        session.getNode("/Subjects/r1/b1/l1").getProperty(IDENTIFIER_PROPERTY).remove();

        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("leaf")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        List<JsonObject> output = new ArrayList<>();
        this.subjectQuickSearchEngine.quickSearch(parameters, this.context.resourceResolver(), output);
        assertEquals(0, output.size());
    }

    @Before
    public void setupRepo()
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.registerAdapter(Resource.class, JsonObject.class, Json.createObjectBuilder().build());
        this.context.build()
                .resource("/Subjects/r1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject1")
                .resource("/Subjects/r1/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch subject1")
                .resource("/Subjects/r1/b1/l1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY, this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch/Leaf")
                                .adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Leaf subject1")
                .resource("/Subjects/r2",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject2")
                .resource("/Subjects/r2/b2",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch subject2")
                .resource("/Subjects/r3",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject3")
                .commit();
    }

}
