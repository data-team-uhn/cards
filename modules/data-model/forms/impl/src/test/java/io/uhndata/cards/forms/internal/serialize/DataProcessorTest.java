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
package io.uhndata.cards.forms.internal.serialize;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DataProcessor}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DataProcessorTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String ANSWER_SECTION_TYPE = "cards:AnswerSection";
    private static final String ANSWER_TYPE = "cards:Answer";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestQuestionnaire";
    private static final String TEST_QUESTION_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_1";
    private static final String TEST_QUESTION_2_PATH = "/Questionnaires/TestQuestionnaire/section_1/question_2";
    private static final String TEST_SECTION_PATH = "/Questionnaires/TestQuestionnaire/section_1";
    private static final String TEST_COMPUTED_QUESTIONNAIRE_PATH = "/Questionnaires/TestComputedQuestionnaire";
    private static final String TEST_COMPUTED_QUESTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/computed_question";
    private static final String TEST_LONG_QUESTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section/long_question";
    private static final String TEST_COMPUTED_SECTION_PATH =
            "/Questionnaires/TestComputedQuestionnaire/from_long_to_computed_section";
    private static final String TEST_SUBJECT_PATH = "/Subjects/Test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SECTION_PROPERTY = "section";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String NAME = "data";
    private static final int PRIORITY = 90;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private DataProcessor dataProcessor;

    @Test
    public void getNameTest()
    {
        Assert.assertEquals(NAME, this.dataProcessor.getName());
    }

    @Test
    public void getPriorityTest()
    {
        Assert.assertEquals(PRIORITY, this.dataProcessor.getPriority());
    }

    @Test
    public void canProcessForSubjectReturnsTrue()
    {
        Resource subject = this.context.resourceResolver().getResource(TEST_SUBJECT_PATH);
        Assert.assertTrue(this.dataProcessor.canProcess(subject));
    }

    @Test
    public void canProcessForQuestionnaireReturnsTrue()
    {
        Resource questionnaire = this.context.resourceResolver().getResource(TEST_QUESTIONNAIRE_PATH);
        Assert.assertTrue(this.dataProcessor.canProcess(questionnaire));
    }

    @Test
    public void canProcessForSubjectTypeReturnsFalse()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        Assert.assertFalse(this.dataProcessor.canProcess(subjectType));
    }

    @Test
    public void startForSubjectResource() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        Resource resource = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        String resolutionPathInfo = ".data.dataFilter:status=INCOMPLETE.dataOption:descendantData=true.deep.json";

        when(resource.getPath()).thenReturn(TEST_SUBJECT_PATH);
        when(resource.getResourceResolver()).thenReturn(resourceResolver);
        when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn(resolutionPathInfo);
        this.dataProcessor.start(resource);

        ThreadLocal<ResourceResolver> resolverActual = (ThreadLocal<ResourceResolver>) getAccessedField("resolver");
        Assert.assertEquals(resourceResolver, resolverActual.get());

        ThreadLocal<String> selectorsActual = (ThreadLocal<String>) getAccessedField("selectors");
        Assert.assertEquals(resolutionPathInfo, selectorsActual.get());

        ThreadLocal<String> rootNodeActual = (ThreadLocal<String>) getAccessedField("rootNode");
        Assert.assertEquals(TEST_SUBJECT_PATH, rootNodeActual.get());

        ThreadLocal<Map<String, String>> filtersActual = (ThreadLocal<Map<String, String>>) getAccessedField("filters");
        Assert.assertEquals(1, filtersActual.get().size());
        Assert.assertTrue(filtersActual.get().containsKey("status"));
        Assert.assertEquals("INCOMPLETE", filtersActual.get().get("status"));

        ThreadLocal<Map<String, String>> optionsActual = (ThreadLocal<Map<String, String>>) getAccessedField("options");
        Assert.assertEquals(1, optionsActual.get().size());
        Assert.assertTrue(optionsActual.get().containsKey("descendantData"));
        Assert.assertEquals("true", optionsActual.get().get("descendantData"));

        ThreadLocal<Object> displayLevelActual =
                (ThreadLocal<Object>) getAccessedField("displayLevel");
        Assert.assertEquals("true", displayLevelActual.get().toString());
    }

    @Test
    public void startForSubjectResourceWithNumericDescendantDataOptions() throws RepositoryException,
            NoSuchFieldException, IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        Resource resource = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        String resolutionPathInfo = ".data.dataOption:descendantData=2.deep.json";

        when(resource.getPath()).thenReturn(TEST_SUBJECT_PATH);
        when(resource.getResourceResolver()).thenReturn(resourceResolver);
        when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn(resolutionPathInfo);
        this.dataProcessor.start(resource);

        ThreadLocal<Map<String, String>> optionsActual = (ThreadLocal<Map<String, String>>) getAccessedField("options");
        Assert.assertEquals(1, optionsActual.get().size());
        Assert.assertTrue(optionsActual.get().containsKey("descendantData"));
        Assert.assertEquals("2", optionsActual.get().get("descendantData"));

        ThreadLocal<Object> displayLevelActual =
                (ThreadLocal<Object>) getAccessedField("displayLevel");
        Assert.assertEquals("2", displayLevelActual.get().toString());
    }

    @Test
    public void leaveForNotChildNode() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        ThreadLocal<String> rootNodeActual = (ThreadLocal<String>) getAccessedField("rootNode");
        rootNodeActual.set(TEST_SUBJECT_PATH);

        Node currentNode = session.getNode(TEST_FORM_PATH);
        JsonObjectBuilder json = Json.createObjectBuilder();
        this.dataProcessor.leave(currentNode, json, mock(Function.class));
        Assert.assertTrue(json.build().isEmpty());
    }

    @Test
    public void leaveForBranchSubjectNode() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        ThreadLocal<String> rootNodeActual = (ThreadLocal<String>) getAccessedField("rootNode");
        rootNodeActual.set(TEST_SUBJECT_PATH);

        ThreadLocal<Object> displayLevelActual = (ThreadLocal<Object>) getAccessedField("displayLevel");
        displayLevelActual.set(true);

        ThreadLocal<Map<String, String>> uuidsWithEntityFilterActual =
                (ThreadLocal<Map<String, String>>) getAccessedField("uuidsWithEntityFilter");

        Node currentNode = session.getNode("/Subjects/Test/TestTumor");
        JsonObjectBuilder json = Json.createObjectBuilder();
        this.dataProcessor.leave(currentNode, json, mock(Function.class));
        Assert.assertTrue(uuidsWithEntityFilterActual.get().containsKey(currentNode.getIdentifier()));
        Assert.assertEquals("subject", uuidsWithEntityFilterActual.get().get(currentNode.getIdentifier()));
        Assert.assertTrue(json.build().isEmpty());
    }

    @Test
    public void leaveForRootSubjectNode() throws RepositoryException, NoSuchFieldException, IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        ThreadLocal<ResourceResolver> resolverActual = (ThreadLocal<ResourceResolver>) getAccessedField("resolver");
        resolverActual.set(resourceResolver);

        ThreadLocal<String> rootNodeActual = (ThreadLocal<String>) getAccessedField("rootNode");
        rootNodeActual.set(TEST_SUBJECT_PATH);

        ThreadLocal<Object> displayLevelActual = (ThreadLocal<Object>) getAccessedField("displayLevel");
        displayLevelActual.set(0);

        ThreadLocal<Map<String, String>> uuidsWithEntityFilterActual =
                (ThreadLocal<Map<String, String>>) getAccessedField("uuidsWithEntityFilter");

        ThreadLocal<String> selectorsActual = (ThreadLocal<String>) getAccessedField("selectors");
        selectorsActual.set(generateResolutionPathInfo());

        ThreadLocal<Map<String, String>> filters = (ThreadLocal<Map<String, String>>) getAccessedField("filters");
        filters.set(generateFilters());

        ThreadLocal<Map<String, String>> optionsActual = (ThreadLocal<Map<String, String>>) getAccessedField("options");
        optionsActual.set(Map.of("descendantData", "0"));

        Node currentNode = session.getNode(TEST_SUBJECT_PATH);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dataProcessor.leave(currentNode, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertFalse(uuidsWithEntityFilterActual.get().containsKey(currentNode.getIdentifier()));
        Assert.assertEquals(4, jsonObject.size());
        Assert.assertTrue(jsonObject.containsKey("exportDate"));
        Assert.assertTrue(jsonObject.containsKey("Test Questionnaire"));
        Assert.assertTrue(jsonObject.containsKey("dataFilters"));
        Assert.assertEquals(8, jsonObject.getJsonObject("dataFilters").size());
        Assert.assertTrue(jsonObject.containsKey("dataOptions"));
        Assert.assertEquals(1, jsonObject.getJsonObject("dataOptions").size());
    }

    @Test
    public void leaveForRootSubjectNodeWithFormSelectorsOption() throws RepositoryException, NoSuchFieldException,
            IllegalAccessException
    {
        ResourceResolver resourceResolver = this.context.resourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        commitResources(session);

        ThreadLocal<ResourceResolver> resolverActual = (ThreadLocal<ResourceResolver>) getAccessedField("resolver");
        resolverActual.set(resourceResolver);

        ThreadLocal<String> rootNodeActual = (ThreadLocal<String>) getAccessedField("rootNode");
        rootNodeActual.set(TEST_SUBJECT_PATH);

        ThreadLocal<Object> displayLevelActual = (ThreadLocal<Object>) getAccessedField("displayLevel");
        displayLevelActual.set(0);

        ThreadLocal<Map<String, String>> uuidsWithEntityFilterActual =
                (ThreadLocal<Map<String, String>>) getAccessedField("uuidsWithEntityFilter");

        ThreadLocal<String> selectorsActual = (ThreadLocal<String>) getAccessedField("selectors");
        selectorsActual.set(generateResolutionPathInfo());

        ThreadLocal<Map<String, String>> filters = (ThreadLocal<Map<String, String>>) getAccessedField("filters");
        filters.set(generateFilters());

        ThreadLocal<Map<String, String>> optionsActual = (ThreadLocal<Map<String, String>>) getAccessedField("options");
        optionsActual.set(Map.of("descendantData", "0", "formSelectors", "formSelectors=-dereference%5C"));

        Node currentNode = session.getNode(TEST_SUBJECT_PATH);
        JsonObjectBuilder json = Json.createObjectBuilder();

        this.dataProcessor.leave(currentNode, json, mock(Function.class));
        JsonObject jsonObject = json.build();
        Assert.assertFalse(uuidsWithEntityFilterActual.get().containsKey(currentNode.getIdentifier()));
        Assert.assertEquals(4, jsonObject.size());
        Assert.assertEquals(2, jsonObject.getJsonObject("dataOptions").size());
    }

    @Test
    public void end() throws NoSuchFieldException, IllegalAccessException
    {
        ThreadLocal<Object> displayLevelActual = (ThreadLocal<Object>) getAccessedField("displayLevel");
        displayLevelActual.set(true);

        ThreadLocal<Map<String, String>> uuidsWithEntityFilterActual =
                (ThreadLocal<Map<String, String>>) getAccessedField("uuidsWithEntityFilter");
        uuidsWithEntityFilterActual.set(Map.of("uuid", "subject"));

        this.dataProcessor.end(mock(Resource.class));
        Assert.assertTrue(uuidsWithEntityFilterActual.get().isEmpty());
        Assert.assertEquals(0, displayLevelActual.get());
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
        this.context.load().json("/ComputedQuestionnairesPlain.json", TEST_COMPUTED_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH, NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                .commit();
        this.context.registerAdapter(Resource.class, JsonObject.class, Json.createObjectBuilder().build());
    }

    private void commitResources(Session session) throws RepositoryException
    {
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node section = session.getNode(TEST_SECTION_PATH);
        Node question = session.getNode(TEST_QUESTION_PATH);
        Node question2 = session.getNode(TEST_QUESTION_2_PATH);

        Node computedQuestionnaire = session.getNode(TEST_COMPUTED_QUESTIONNAIRE_PATH);
        Node computedSection = session.getNode(TEST_COMPUTED_SECTION_PATH);
        Node longQuestion = session.getNode(TEST_LONG_QUESTION_PATH);
        Node computedQuestion = session.getNode(TEST_COMPUTED_QUESTION_PATH);

        this.context.build()
                .resource("/Subjects/Test/TestTumor", NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class))
                .resource("/Subjects/Test/TestTumor/TestTumorRegion", NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch/Leaf")
                                .adaptTo(Node.class))
                .resource("/Subjects/Test_2", NODE_TYPE, SUBJECT_TYPE, "type",
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class))
                // form1 for Subject
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, subject,
                        "relatedSubjects", List.of(subject).toArray())
                .resource("/Forms/f1/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, section)
                .resource("/Forms/f1/s1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
                // form for subject 2
                .resource("/Forms/f2",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, questionnaire,
                        SUBJECT_PROPERTY, session.getNode("/Subjects/Test_2"))
                .resource("/Forms/f2/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, section)
                .resource("/Forms/f2/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question)
                .resource("/Forms/f2/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, question2)
                // form for subject/branch
                .resource("/Forms/f3",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, computedQuestionnaire,
                        SUBJECT_PROPERTY, session.getNode("/Subjects/Test/TestTumor"))
                .resource("/Forms/f3/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, computedSection)
                .resource("/Forms/f3/s1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, longQuestion)
                .resource("/Forms/f3/s1/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, computedQuestion)
                .resource("/Forms/f3/s1/a3", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, computedQuestion)
                // form for subject/branch/leaf and computedQuestionnaire
                .resource("/Forms/f4",
                        NODE_TYPE, FORM_TYPE,
                        QUESTIONNAIRE_PROPERTY, computedQuestionnaire,
                        SUBJECT_PROPERTY, session.getNode("/Subjects/Test/TestTumor/TestTumorRegion"))
                .resource("/Forms/f4/s1", NODE_TYPE, ANSWER_SECTION_TYPE, SECTION_PROPERTY, computedSection)
                .resource("/Forms/f4/s1/a1", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, longQuestion)
                .resource("/Forms/f4/s1/a2", NODE_TYPE, ANSWER_TYPE, QUESTION_PROPERTY, computedQuestion)
                .commit();
    }

    private Object getAccessedField(String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = this.dataProcessor.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(this.dataProcessor);
    }

    private String generateResolutionPathInfo()
    {
        return String.format(".data.dataFilter:createdAfter=%s.dataFilter:createdBefore=%s.dataFilter:createdBy=%s"
                        + ".dataFilter:status=%s.dataFilter:statusNot=%s.dataFilter:modifiedAfter=%s"
                        + ".dataFilter:modifiedBefore=%s.dataFilter:nonExistingFilter=%s.dataOption:descendantData=%s"
                        + ".deep.json",
                java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(1),
                "admin", "INCOMPLETE", "COMPLETE", java.time.LocalDate.now().minusDays(1),
                java.time.LocalDate.now().plusDays(1), "nonExistingFilter", 0);
    }

    private Map<String, String> generateFilters()
    {
        Map<String, String> filters = new HashMap<>();
        filters.put("createdAfter", java.time.LocalDate.now().minusDays(1).toString());
        filters.put("createdBefore", java.time.LocalDate.now().plusDays(1).toString());
        filters.put("createdBy", "admin");
        filters.put("status", "INCOMPLETE");
        filters.put("statusNot", "COMPLETE");
        filters.put("modifiedAfter", java.time.LocalDate.now().minusDays(1).toString());
        filters.put("modifiedBefore", java.time.LocalDate.now().plusDays(1).toString());
        filters.put("nonExistingFilter", "nonExistingFilter");
        return filters;
    }

}
