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
package io.uhndata.cards;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataImportServlet}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DataImportServletTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_OPTION_TYPE = "cards:AnswerOption";
    private static final String FORM_TYPE = "cards:Form";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String RELATED_SUBJECTS_PROPERTY = "relatedSubjects";
    private static final String VALUE_PROPERTY = "value";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String LABEL_PROPERTY = "label";

    // Paths
    private static final String ROOT_FORM_PATH = "/Forms";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_SUBJECT_2_PATH = "/Subjects/r2";
    private static final String ROOT_SUBJECT_TYPE = "/SubjectTypes/Root";
    private static final String BRANCH_SUBJECT_TYPE = "/SubjectTypes/Root/Branch";
    private static final String PATIENT_SUBJECT_TYPE = "/SubjectTypes/Patient";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";
    private static final String TEST_TEXT_QUESTIONNAIRE_PATH = "/Questionnaires/TestTextQuestionnaire";

    // Parameters
    private static final String SUBJECT_TYPE_PARAMETER = ":subjectType";
    private static final String DATA_PARAMETER = ":data";
    private static final String QUESTIONNAIRE_PARAMETER = ":questionnaire";
    private static final String PATCH_PARAMETER = ":patch";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DataImportServlet dataImportServlet;

    private BundleContext slingBundleContext;

    private ResourceResolver resourceResolver;

    @Test
    public void doPostWithoutDataTypeParameterValueSendsError() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("Required parameter \":data\" missing", response.getStatusMessage());
    }

    @Test
    public void doPostWithoutQuestionnaireTypeParameterValueSendsError() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, "",
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("Required parameter \":questionnaire\" missing", response.getStatusMessage());
    }

    @Test
    public void doPostWithInvalidQuestionnaireTypeParameterValueSendsError() throws IOException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        String invalidQuestionnaireName = "/Questionnaires/InvalidQuestionnaire";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, "",
                QUESTIONNAIRE_PARAMETER, invalidQuestionnaireName,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("Invalid questionnaire name " + invalidQuestionnaireName, response.getStatusMessage());
    }

    @Test
    public void doPostUpdatesValueInTextAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tText Question\tText Question_notes\r\n"
                + "f3\tRoot Subject\tnewValue\tnewNote";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_TEXT_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        Node form = this.context.resourceResolver().getResource("/Forms/f3").adaptTo(Node.class);
        assertEquals("newValue", form.getNode("a1").getProperty(VALUE_PROPERTY).getString());
        assertEquals("newNote", form.getNode("a1").getProperty("note").getString());
    }

    @Test
    public void doPostCreatesValueInTextAnswerOfDoubleNestedSection() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tText Question\r\n"
                + "f1\tRoot Subject\tnewValue";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        Node form = this.context.resourceResolver().getResource("/Forms/f1").adaptTo(Node.class);
        assertTrue(form.getNode("s1").getNode("a6").hasProperty(VALUE_PROPERTY));
        assertEquals(1, form.getNode("s1").getNode("a6").getProperty(VALUE_PROPERTY).getValues().length);
        assertEquals("newValue",
                form.getNode("s1").getNode("a6").getProperty(VALUE_PROPERTY).getValues()[0].getString());
    }

    @Test
    public void doPostCreatesValueInLongAnswerForChildSubject() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tBranch ID\tLong Question\r\n"
                + "f2\tRoot Subject\tBranch Subject\t100";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, new String[]{ROOT_SUBJECT_TYPE, BRANCH_SUBJECT_TYPE},
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);
        Node form = this.context.resourceResolver().getResource("/Forms/f2").adaptTo(Node.class);
        assertEquals(100, form.getNode("a1").getProperty(VALUE_PROPERTY).getLong());
    }

    @Test
    public void doPostCreatesSubjectOfPatientTypeAndCreatesNewFormWithTextValueAnswers() throws IOException,
            RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tPatient ID\tText Question\tText2 Question\r\n"
                + "f5\tPatient Subject\tnewValue\tnewValue2";
        request.setParameterMap(Map.of(
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node formBySubject = getFormOfPatientSubjectType();
        NodeIterator sectionAnswers = formBySubject.getNodes().nextNode().getNodes();
        assertEquals("newValue", sectionAnswers.nextNode().getProperty(VALUE_PROPERTY).getValues()[0].getString());
        assertEquals("newValue2", sectionAnswers.nextNode().getProperty(VALUE_PROPERTY).getValues()[0].getString());
    }

    @Test
    public void doPostCreatesNewFormWithLongValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tLong Question\r\n"
                + "f5\tRoot2 Subject\t100";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());

        assertEquals(100, form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getLong());
    }

    @Test
    public void doPostCreatesNewFormWithDoubleValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tDouble Question\r\n"
                + "f5\tRoot2 Subject\t100";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());

        assertEquals(100.0, form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getDouble(), 0);
    }

    @Test
    public void doPostCreatesNewFormWithDecimalValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tDecimal Question\r\n"
                + "f5\tRoot2 Subject\t100";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());

        assertEquals(BigDecimal.valueOf(100), form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getDecimal());
    }

    @Test
    public void doPostCreatesNewFormWithBooleanValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tBoolean Question\r\n"
                + "f5\tRoot2 Subject\ttrue";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());
        assertEquals(1, form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getLong());
    }

    @Test
    public void doPostCreatesNewFormWithVocabularyValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tOptions Question\r\n"
                + "f5\tRoot2 Subject\t/Vocabularies/Option1";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());
        assertEquals("/Vocabularies/Option1", form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getString());
    }

    @Test
    public void doPostCreatesNewFormWithVocabularyValueAnswerIgnoresCase() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tOptions Question\r\n"
                + "f5\tRoot2 Subject\t/VOCABULARIES/OPTION2";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());
        assertEquals("/Vocabularies/Option2", form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getString());
    }

    @Test
    public void doPostCreatesNewFormWithTimeValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tTime Question\r\n"
                + "f5\tRoot2 Subject\t01:23:13";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());
        assertEquals("01:23:13", form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getString());
    }

    @Test
    public void doPostCreatesNewFormWithDateValueAnswer() throws IOException, RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);
        final String date = "2023-01-01";
        String dataCsv = "Identifier\tRoot ID\tDate Question\r\n"
                + "f5\tRoot2 Subject\t" + date;
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals(date, sdf.format(form.getNodes().nextNode().getProperty(VALUE_PROPERTY).getDate().getTime()));
    }

    @Test
    public void doPostCatchesRepositoryException() throws RepositoryException
    {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Session mockedSession = mock(Session.class);
        Workspace mockedWorkspace = mock(Workspace.class);
        when(resolver.getResource(eq(ROOT_FORM_PATH))).thenReturn(this.resourceResolver.getResource(ROOT_FORM_PATH));
        when(resolver.getResource(eq("/Subjects"))).thenReturn(this.resourceResolver.getResource("/Subjects"));
        when(resolver.adaptTo(Session.class)).thenReturn(mockedSession);
        when(mockedSession.getWorkspace()).thenReturn(mockedWorkspace);
        when(mockedWorkspace.getQueryManager()).thenThrow(new RepositoryException());

        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resolver, this.slingBundleContext);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        Assertions.assertThatCode(() -> this.dataImportServlet.doPost(request, response)).doesNotThrowAnyException();
    }

    @Test
    public void doPostCatchesNumberFormatExceptionAndCreatesNewFormWithAnswerWithoutValue() throws IOException,
            RepositoryException
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tRoot ID\tDouble Question\r\n"
                + "f5\tRoot2 Subject\tnotParsableValue";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "false"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        this.dataImportServlet.doPost(request, response);

        Node subject = this.context.resourceResolver().getResource(TEST_SUBJECT_2_PATH).adaptTo(Node.class);
        Node form = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subject.getIdentifier());

        assertFalse(form.getNodes().nextNode().hasProperty(VALUE_PROPERTY));
    }

    @Test
    public void doPostForUnrealSubject()
    {
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(this.resourceResolver, this.slingBundleContext);

        String dataCsv = "Identifier\tUnreal ID\tText Question\r\n"
                + "f3\tUnreal Subject\tnewValue";
        request.setParameterMap(Map.of(
                SUBJECT_TYPE_PARAMETER, ROOT_SUBJECT_TYPE,
                DATA_PARAMETER, dataCsv,
                QUESTIONNAIRE_PARAMETER, TEST_TEXT_QUESTIONNAIRE_PATH,
                PATCH_PARAMETER, "true"
        ));

        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        Assertions.assertThatCode(() -> this.dataImportServlet.doPost(request, response)).doesNotThrowAnyException();
    }

    @Before
    public void setUp() throws RepositoryException, LoginException
    {
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource(ROOT_FORM_PATH, NODE_TYPE, "cards:FormsHomepage")
                .resource("/Vocabularies", NODE_TYPE, "sling:Folder")
                .commit();

        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/TextQuestionnaires.json", TEST_TEXT_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", ROOT_SUBJECT_TYPE);
        this.context.load().json("/SubjectTypesPatient.json", PATIENT_SUBJECT_TYPE);

        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource(ROOT_SUBJECT_TYPE).adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root Subject")
                .resource(TEST_SUBJECT_PATH + "/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource(BRANCH_SUBJECT_TYPE).adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch Subject")

                .resource(TEST_SUBJECT_2_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource(ROOT_SUBJECT_TYPE).adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root2 Subject")
                .commit();

        Node rootSubject = session.getNode(TEST_SUBJECT_PATH);
        Node branchSubject = session.getNode(TEST_SUBJECT_PATH + "/b1");

        this.context.build()
                .resource("/Vocabularies/Option1",
                        NODE_TYPE, ANSWER_OPTION_TYPE,
                        LABEL_PROPERTY, "Option 1",
                        VALUE_PROPERTY, "O1")
                .resource("/Vocabularies/Option2",
                        NODE_TYPE, ANSWER_OPTION_TYPE,
                        LABEL_PROPERTY, "Option 2",
                        VALUE_PROPERTY, "O2")

                .resource("/Forms/f1",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, rootSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject).toArray())
                .resource("/Forms/f1/s1",
                        NODE_TYPE, "cards:AnswerSection",
                        "section", session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1"))
                .resource("/Forms/f1/s1/a6",
                        NODE_TYPE, "cards:TextAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/section_1/question_6"))

                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, branchSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject, branchSubject).toArray())
                .resource("/Forms/f2/a1",
                        NODE_TYPE, "cards:LongAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1"))

                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, rootSubject,
                        QUESTIONNAIRE_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH),
                        RELATED_SUBJECTS_PROPERTY, List.of(rootSubject).toArray())
                .resource("/Forms/f3/a1",
                        NODE_TYPE, "cards:TextAnswer",
                        QUESTION_PROPERTY, session.getNode(TEST_TEXT_QUESTIONNAIRE_PATH + "/question_1"),
                        VALUE_PROPERTY, "12345")
                .commit();

        this.slingBundleContext = this.context.bundleContext();
        this.resourceResolver = this.slingBundleContext
                .getService(this.slingBundleContext.getServiceReference(ResourceResolverFactory.class))
                .getServiceResourceResolver(null);

    }

    private Node getFormOfPatientSubjectType() throws RepositoryException
    {
        Node subjectType = this.context.resourceResolver().getResource(PATIENT_SUBJECT_TYPE).adaptTo(Node.class);
        Node subjectBySubjectType = getNodeBySearchParam("Subject", TYPE_PROPERTY, subjectType.getIdentifier());
        assertNotNull(subjectBySubjectType);

        Node formBySubject = getNodeBySearchParam("Form", SUBJECT_PROPERTY, subjectBySubjectType.getIdentifier());
        assertNotNull(formBySubject);
        return formBySubject;
    }

    private Node getNodeBySearchParam(String nodeType, String searchParamName, String searchParamValue)
            throws RepositoryException
    {
        String query = String.format("select n from [cards:%s] as n where n.%s = '%s'",
                nodeType, searchParamName, searchParamValue);
        Query queryObj = this.resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager()
                .createQuery(query, "JCR-SQL2");
        queryObj.setLimit(1);
        NodeIterator nodeResult = queryObj.execute().getNodes();

        if (nodeResult.hasNext()) {
            return nodeResult.nextNode();
        }
        return null;
    }

}
