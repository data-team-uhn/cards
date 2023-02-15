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
package io.uhndata.cards.forms.internal;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.builder.impl.SlingHttpServletRequestImpl;
import org.apache.sling.api.request.builder.impl.SlingHttpServletResponseImpl;
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

/**
 * Unit tests for {@link QuestionnaireCSVServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireCSVServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private QuestionnaireCSVServlet questionnaireCSVServlet;

    @Test
    public void doGetTest() throws IOException {
        this.context.addModelsForPackage("io.uhndata.cards.serialize");
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        SlingHttpServletRequest request = new SlingHttpServletRequestImpl(questionnaire);
        SlingHttpServletResponse response = new SlingHttpServletResponseImpl();
        this.questionnaireCSVServlet.doGet(request, response);

        Assert.assertEquals("UTF-8", response.getCharacterEncoding());
        Assert.assertTrue(response.containsHeader("Content-disposition"));
    }


    @Before
    public void setupRepo() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
    }

}
