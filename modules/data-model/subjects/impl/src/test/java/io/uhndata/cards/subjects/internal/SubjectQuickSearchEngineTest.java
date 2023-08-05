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
import javax.jcr.Workspace;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.uhndata.cards.spi.QuickSearchEngine;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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

import static org.junit.Assert.*;
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

        QuickSearchEngine.Results output = this.subjectQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        for (int numberOfFoundMatches = 0; numberOfFoundMatches < 2; numberOfFoundMatches++) {
            assertTrue(output.hasNext());
            assertNotNull(output.next());
        }
        assertFalse(output.hasNext());
    }

    @Test
    public void quickSearchForFoundItemWithoutIdentifierPropertyCatchesRepositoryException() throws RepositoryException
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("leaf")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        QuickSearchEngine.Results output = this.subjectQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node foundItem = session.getNode("/Subjects/r1/b1/l1");
        foundItem.getProperty("identifier").remove();
        assertTrue(output.hasNext());
        JsonValue emptyJsonValue = output.next();
        assertNotNull(emptyJsonValue);
        assertEquals(JsonValue.EMPTY_JSON_OBJECT, emptyJsonValue);
        assertFalse(output.hasNext());
    }

    @Test
    public void quickSearchCatchesRepositoryExceptionAndReturnsEmptyResults() throws RepositoryException
    {
        SearchParameters parameters = SearchParametersFactory.newSearchParameters()
                .withQuery("branch")
                .withType(QUICK_SEARCH_PARAMETER_TYPE)
                .build();

        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getQueryManager()).thenThrow(new RepositoryException());
        QuickSearchEngine.Results output = this.subjectQuickSearchEngine.quickSearch(parameters, resourceResolver);
        assertFalse(output.hasNext());
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

        QuickSearchEngine.Results output = this.subjectQuickSearchEngine.quickSearch(parameters,
                this.context.resourceResolver());
        assertFalse(output.hasNext());
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
